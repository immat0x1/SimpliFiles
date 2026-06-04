package org.simplifiles.archive

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.security.DuplicatePolicy
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveValidationException
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZipArchiveEdgeCaseTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `empty zip can be inspected validated and extracted`() {
        val zip = createZip()

        val inspection = SimpliFiles.archive(zip).inspect()
        val report = SimpliFiles.archive(zip).validate()

        assertEquals(ArchiveFormat.ZIP, inspection.format)
        assertEquals(0, inspection.entryCount)
        assertTrue(report.isSafe)

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            assertTrue(archive.files.isEmpty())
        }
    }

    @Test
    fun `entry paths are normalized for inspection and extraction`() {
        val zip = createZip(
            "dir/./file.txt" to "first".toByteArray(),
            "dir//nested/second.txt" to "second".toByteArray(),
        )

        val inspection = SimpliFiles.archive(zip).inspect()
        assertEquals(
            listOf("dir/file.txt", "dir/nested/second.txt"),
            inspection.entries.map { it.normalizedPath },
        )

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            assertEquals("first", archive.file("dir/file.txt").readText())
            assertEquals("second", archive.file("dir/nested/second.txt").readText())
        }
    }

    @Test
    fun `validate rejects backslash parent traversal paths`() {
        val zip = createZip("..\\evil.txt" to "payload".toByteArray())

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.traversal" })
    }

    @Test
    fun `validate rejects windows absolute paths`() {
        val zip = createZip("C:\\Users\\demo\\evil.txt" to "payload".toByteArray())

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.absolute" })
    }

    @Test
    fun `validate rejects bare windows drive paths`() {
        val zip = createZip("C:" to "payload".toByteArray())

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.absolute" })
    }

    @Test
    fun `validate rejects empty entry names`() {
        val zip = createStoredZipAllowingDuplicateNames("" to "payload".toByteArray())

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.empty" })
    }

    @Test
    fun `validate rejects file directory conflicts`() {
        val zip = createZip(
            "conflict" to "file".toByteArray(),
            "conflict/nested.txt" to "nested".toByteArray(),
        )

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.conflict" })
    }

    @Test
    fun `validate rejects file directory conflicts when parent file appears later`() {
        val zip = createZip(
            "conflict/nested.txt" to "nested".toByteArray(),
            "conflict" to "file".toByteArray(),
        )

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.conflict" })
    }

    @Test
    fun `validate rejects directory conflicts when matching file appears first`() {
        val zip = createZip(
            "conflict" to "file".toByteArray(),
            "conflict/" to null,
        )

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.conflict" })
    }

    @Test
    fun `extract rejects file directory conflicts before creating target`() {
        val zip = createZip(
            "conflict" to "file".toByteArray(),
            "conflict/nested.txt" to "nested".toByteArray(),
        )
        val target = tempDir.resolve("conflict-output")

        kotlin.test.assertFailsWith<ArchiveValidationException> {
            SimpliFiles.archive(zip).extractTo(target)
        }

        assertFalse(Files.exists(target))
    }

    @Test
    fun `validate rejects suspicious compression ratio`() {
        val zip = createZip("large.txt" to ByteArray(16_384) { 'a'.code.toByte() })

        val report = SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxCompressionRatio = 2.0))
            .validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.compression_ratio.too_high" })
    }

    @Test
    fun `validate rejects duplicate paths by default`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )

        val report = SimpliFiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.duplicate" })
    }

    @Test
    fun `plan reports unsafe duplicate paths by default`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )

        val plan = SimpliFiles.archive(zip).planExtractionTo(tempDir.resolve("duplicate-plan"))

        assertFalse(plan.isSafe)
        assertTrue(plan.entries.isEmpty())
        assertTrue(plan.validationReport.issues.any { it.code == "archive.entry.duplicate" })
    }

    @Test
    fun `plan uses keep first duplicate policy`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )

        val plan = SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(duplicatePolicy = DuplicatePolicy.KEEP_FIRST))
            .planExtractionTo(tempDir.resolve("keep-first-plan"))

        assertTrue(plan.isSafe)
        assertEquals(
            listOf(ArchivePlannedAction.CREATE_FILE, ArchivePlannedAction.SKIP_DUPLICATE),
            plan.entries.map { it.action },
        )
        assertEquals(listOf("same.txt", "same.txt"), plan.entries.map { it.normalizedPath })
    }

    @Test
    fun `plan uses keep last duplicate policy`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )

        val plan = SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(duplicatePolicy = DuplicatePolicy.KEEP_LAST))
            .planExtractionTo(tempDir.resolve("keep-last-plan"))

        assertTrue(plan.isSafe)
        assertEquals(
            listOf(ArchivePlannedAction.CREATE_FILE, ArchivePlannedAction.REPLACE_DUPLICATE),
            plan.entries.map { it.action },
        )
        assertEquals(listOf("same.txt", "same.txt"), plan.entries.map { it.normalizedPath })
    }

    @Test
    fun `plan uses rename duplicate policy`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
            "same.txt" to "third".toByteArray(),
        )

        val plan = SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(duplicatePolicy = DuplicatePolicy.RENAME))
            .planExtractionTo(tempDir.resolve("rename-plan"))

        assertTrue(plan.isSafe)
        assertEquals(
            listOf(
                ArchivePlannedAction.CREATE_FILE,
                ArchivePlannedAction.RENAME_DUPLICATE,
                ArchivePlannedAction.RENAME_DUPLICATE,
            ),
            plan.entries.map { it.action },
        )
        assertEquals(listOf("same.txt", "same-1.txt", "same-2.txt"), plan.entries.map { it.normalizedPath })
    }

    @Test
    fun `extract uses keep first duplicate policy`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )

        SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(duplicatePolicy = DuplicatePolicy.KEEP_FIRST))
            .extractToTemp()
            .use { archive ->
                assertEquals("first", archive.file("same.txt").readText())
            }
    }

    @Test
    fun `extract uses keep last duplicate policy`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )

        SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(duplicatePolicy = DuplicatePolicy.KEEP_LAST))
            .extractToTemp()
            .use { archive ->
                assertEquals("second", archive.file("same.txt").readText())
            }
    }

    @Test
    fun `extract uses rename duplicate policy`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
            "same.txt" to "third".toByteArray(),
        )

        SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(duplicatePolicy = DuplicatePolicy.RENAME))
            .extractToTemp()
            .use { archive ->
                assertEquals(
                    listOf("same-1.txt", "same-2.txt", "same.txt"),
                    archive.files.map { it.path }.sorted(),
                )
                assertEquals("first", archive.file("same.txt").readText())
                assertEquals("second", archive.file("same-1.txt").readText())
                assertEquals("third", archive.file("same-2.txt").readText())
            }
    }

    @Test
    fun `extract default duplicate policy fails before creating target`() {
        val zip = createStoredZipAllowingDuplicateNames(
            "same.txt" to "first".toByteArray(),
            "same.txt" to "second".toByteArray(),
        )
        val target = tempDir.resolve("duplicate-output")

        kotlin.test.assertFailsWith<ArchiveValidationException> {
            SimpliFiles.archive(zip).extractTo(target)
        }

        assertFalse(Files.exists(target))
    }

    private fun createZip(vararg entries: Pair<String, ByteArray?>): Path {
        val zip = tempDir.resolve("archive-${System.nanoTime()}.zip")

        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            for ((path, content) in entries) {
                output.putNextEntry(ZipEntry(path))
                if (content != null) {
                    output.write(content)
                }
                output.closeEntry()
            }
        }

        return zip
    }

    private fun createStoredZipAllowingDuplicateNames(vararg entries: Pair<String, ByteArray>): Path {
        val zip = tempDir.resolve("duplicate-${System.nanoTime()}.zip")
        zip.writeBytes(buildStoredZip(entries.toList()))
        return zip
    }

    private fun buildStoredZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = LittleEndianByteWriter()
        val centralDirectory = ByteArrayOutputStream()
        val centralWriter = LittleEndianByteWriter(centralDirectory)
        val centralDirectoryStart: Int
        var centralDirectorySize: Int

        for ((name, content) in entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = CRC32().also { it.update(content) }.value
            val localHeaderOffset = output.size

            output.writeInt(0x04034b50)
            output.writeShort(20)
            output.writeShort(0x0800)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(0)
            output.writeInt(crc.toInt())
            output.writeInt(content.size)
            output.writeInt(content.size)
            output.writeShort(nameBytes.size)
            output.writeShort(0)
            output.write(nameBytes)
            output.write(content)

            centralWriter.writeInt(0x02014b50)
            centralWriter.writeShort(20)
            centralWriter.writeShort(20)
            centralWriter.writeShort(0x0800)
            centralWriter.writeShort(0)
            centralWriter.writeShort(0)
            centralWriter.writeShort(0)
            centralWriter.writeInt(crc.toInt())
            centralWriter.writeInt(content.size)
            centralWriter.writeInt(content.size)
            centralWriter.writeShort(nameBytes.size)
            centralWriter.writeShort(0)
            centralWriter.writeShort(0)
            centralWriter.writeShort(0)
            centralWriter.writeShort(0)
            centralWriter.writeInt(0)
            centralWriter.writeInt(localHeaderOffset)
            centralWriter.write(nameBytes)
        }

        centralDirectoryStart = output.size
        val centralDirectoryBytes = centralDirectory.toByteArray()
        centralDirectorySize = centralDirectoryBytes.size
        output.write(centralDirectoryBytes)

        output.writeInt(0x06054b50)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(entries.size)
        output.writeShort(entries.size)
        output.writeInt(centralDirectorySize)
        output.writeInt(centralDirectoryStart)
        output.writeShort(0)

        return output.toByteArray()
    }

    private class LittleEndianByteWriter(
        private val output: ByteArrayOutputStream = ByteArrayOutputStream(),
    ) {
        val size: Int
            get() = output.size()

        fun write(bytes: ByteArray) {
            output.write(bytes)
        }

        fun writeShort(value: Int) {
            output.write(value and 0xff)
            output.write(value ushr 8 and 0xff)
        }

        fun writeInt(value: Int) {
            output.write(value and 0xff)
            output.write(value ushr 8 and 0xff)
            output.write(value ushr 16 and 0xff)
            output.write(value ushr 24 and 0xff)
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
