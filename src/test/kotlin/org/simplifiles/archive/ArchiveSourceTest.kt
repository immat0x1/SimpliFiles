package org.simplifiles.archive

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.Simplifiles
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.exception.ExtractionTargetException
import org.simplifiles.exception.UnsafeArchivePathException
import org.simplifiles.exception.UnsupportedArchiveFormatException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArchiveSourceTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `inspect reads zip entries`() {
        val zip = createZip(
            "dir/" to null,
            "dir/file.txt" to "hello".toByteArray(),
        )

        val inspection = Simplifiles.archive(zip).inspect()

        assertEquals(ArchiveFormat.ZIP, inspection.format)
        assertEquals(2, inspection.entryCount)
        assertEquals(5, inspection.totalKnownUncompressedSize)

        val file = assertNotNull(inspection.entries.singleOrNull { it.path == "dir/file.txt" })
        assertEquals("dir/file.txt", file.normalizedPath)
        assertFalse(file.isDirectory)
        assertEquals(5, file.uncompressedSize)
    }

    @Test
    fun `inspect rejects unsupported formats`() {
        val file = tempDir.resolve("not-archive.txt")
        Files.writeString(file, "plain text")

        assertFailsWith<UnsupportedArchiveFormatException> {
            Simplifiles.archive(file).inspect()
        }
    }

    @Test
    fun `validate reports unsupported formats without throwing`() {
        val file = tempDir.resolve("not-archive.txt")
        Files.writeString(file, "plain text")

        val report = Simplifiles.archive(file).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.format.unsupported" })
    }

    @Test
    fun `validate reports corrupted zip files`() {
        val zip = tempDir.resolve("corrupted.zip")
        zip.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x01))

        val report = Simplifiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertEquals(ArchiveFormat.ZIP, report.format)
        assertTrue(report.issues.any { it.code == "archive.corrupted" })
    }

    @Test
    fun `validate rejects parent traversal paths`() {
        val zip = createZip("../evil.txt" to "payload".toByteArray())

        val report = Simplifiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.traversal" })
    }

    @Test
    fun `validate rejects absolute paths by default`() {
        val zip = createZip("/tmp/evil.txt" to "payload".toByteArray())

        val report = Simplifiles.archive(zip).validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.path.absolute" })
    }

    @Test
    fun `validate allows absolute paths when policy allows them`() {
        val zip = createZip("/tmp/allowed.txt" to "payload".toByteArray())

        val report = Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(allowAbsolutePaths = true))
            .validate()

        assertTrue(report.isSafe)
    }

    @Test
    fun `validate rejects archives over entry limit`() {
        val zip = createZip(
            "first.txt" to "a".toByteArray(),
            "second.txt" to "b".toByteArray(),
        )

        val report = Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxEntries = 1))
            .validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entries.too_many" })
    }

    @Test
    fun `validate rejects entries over single file size limit`() {
        val zip = createZip("large.txt" to "payload".toByteArray())

        val report = Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxSingleFileSize = 3))
            .validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.entry.size.too_large" })
    }

    @Test
    fun `validate rejects archives over total size limit`() {
        val zip = createZip(
            "first.txt" to "abc".toByteArray(),
            "second.txt" to "def".toByteArray(),
        )

        val report = Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxTotalUncompressedSize = 5))
            .validate()

        assertFalse(report.isSafe)
        assertTrue(report.issues.any { it.code == "archive.total_size.too_large" })
    }

    @Test
    fun `extractTo extracts zip files into target directory`() {
        val zip = createZip(
            "dir/" to null,
            "dir/file.txt" to "hello".toByteArray(),
        )
        val target = tempDir.resolve("output")

        val archive = Simplifiles.archive(zip).extractTo(target)

        val file = archive.file("dir/file.txt")
        assertTrue(file.exists)
        assertEquals("hello", file.readText())
        assertEquals(listOf("dir/file.txt"), archive.files.map { it.path })
        assertTrue(target.exists())
    }

    @Test
    fun `extractToTemp deletes temporary files on close`() {
        val zip = createZip("file.txt" to "hello".toByteArray())
        lateinit var root: Path

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            root = archive.root
            assertTrue(root.exists())
            assertEquals("hello", archive.file("file.txt").readText())
        }

        assertFalse(root.exists())
    }

    @Test
    fun `planExtractionTo previews extraction without creating target directory`() {
        val zip = createZip(
            "dir/./file.txt" to "hello".toByteArray(),
            "empty/" to null,
        )
        val target = tempDir.resolve("planned-output")

        val plan = Simplifiles.archive(zip).planExtractionTo(target)

        assertTrue(plan.isSafe)
        assertFalse(target.exists())
        assertEquals(2, plan.totalEntries)
        assertEquals(5, plan.totalBytesToWrite)
        assertEquals(target.toAbsolutePath().normalize(), plan.targetRoot)
        assertEquals(
            listOf(
                ArchivePlannedAction.CREATE_FILE to "dir/file.txt",
                ArchivePlannedAction.CREATE_DIRECTORY to "empty",
            ),
            plan.entries.map { it.action to it.normalizedPath },
        )
        assertEquals(
            target.toAbsolutePath().normalize().resolve("dir/file.txt"),
            plan.entries.first().destinationPath,
        )
    }

    @Test
    fun `planExtractionTo returns validation issues for unsafe archives`() {
        val zip = createZip("../evil.txt" to "payload".toByteArray())
        val target = tempDir.resolve("unsafe-plan-output")

        val plan = Simplifiles.archive(zip).planExtractionTo(target)

        assertFalse(plan.isSafe)
        assertTrue(plan.entries.isEmpty())
        assertTrue(plan.validationReport.issues.any { it.code == "archive.entry.path.traversal" })
        assertFalse(target.exists())
    }

    @Test
    fun `extract reports progress until completion`() {
        val zip = createZip(
            "first.txt" to "hello".toByteArray(),
            "second.txt" to "world".toByteArray(),
        )
        val progressEvents = mutableListOf<ArchiveProgress>()
        val options = ArchiveExtractionOptions(
            progressListener = ArchiveProgressListener { progress ->
                progressEvents += progress
            },
        )

        Simplifiles.archive(zip).extractToTemp(options).use { archive ->
            assertEquals("hello", archive.file("first.txt").readText())
            assertEquals("world", archive.file("second.txt").readText())
        }

        val finalProgress = progressEvents.last()
        assertEquals(2, finalProgress.totalEntries)
        assertEquals(2, finalProgress.entriesProcessed)
        assertEquals(10, finalProgress.totalBytes)
        assertEquals(10, finalProgress.bytesWritten)
        assertTrue(finalProgress.isComplete)
    }

    @Test
    fun `extract uses configured buffer size`() {
        val zip = createZip("large.txt" to ByteArray(32) { it.toByte() })
        val progressEvents = mutableListOf<ArchiveProgress>()
        val options = ArchiveExtractionOptions(
            progressListener = ArchiveProgressListener { progress ->
                if (progress.currentEntryPath == "large.txt" && progress.bytesWritten > 0) {
                    progressEvents += progress
                }
            },
            bufferSize = 5,
        )

        Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxCompressionRatio = 10_000.0))
            .extractToTemp(options)
            .use { archive ->
                assertEquals(32, archive.file("large.txt").size)
            }

        assertTrue(progressEvents.size > 1)
        assertEquals(32, progressEvents.last().bytesWritten)
    }

    @Test
    fun `extraction options reject invalid buffer size`() {
        assertFailsWith<IllegalArgumentException> {
            ArchiveExtractionOptions(bufferSize = 0)
        }
    }

    @Test
    fun `extract cancellation cleans newly created target directory`() {
        val zip = createZip(
            "large.bin" to ByteArray(128 * 1024) { it.toByte() },
            "next.txt" to "next".toByteArray(),
        )
        val target = tempDir.resolve("cancel-output")
        val canceled = AtomicBoolean(false)
        val options = ArchiveExtractionOptions(
            progressListener = ArchiveProgressListener { progress ->
                if (progress.bytesWritten > 0) {
                    canceled.set(true)
                }
            },
            cancellationToken = CancellationToken.fromSupplier(canceled::get),
        )

        assertFailsWith<ArchiveOperationCanceledException> {
            Simplifiles.archive(zip)
                .withPolicy(SecurityPolicy.strict().copy(maxCompressionRatio = 10_000.0))
                .extractTo(target, options)
        }

        assertFalse(target.exists())
    }

    @Test
    fun `pre canceled extraction does not create target directory`() {
        val zip = createZip("file.txt" to "hello".toByteArray())
        val target = tempDir.resolve("pre-canceled-output")
        val options = ArchiveExtractionOptions(
            cancellationToken = CancellationToken { true },
        )

        assertFailsWith<ArchiveOperationCanceledException> {
            Simplifiles.archive(zip).extractTo(target, options)
        }

        assertFalse(target.exists())
    }

    @Test
    fun `extractTo rejects unsafe archives before creating target directory`() {
        val zip = createZip("../evil.txt" to "payload".toByteArray())
        val target = tempDir.resolve("unsafe-output")

        val exception = assertFailsWith<ArchiveValidationException> {
            Simplifiles.archive(zip).extractTo(target)
        }

        assertTrue(exception.report.issues.any { it.code == "archive.entry.path.traversal" })
        assertFalse(target.exists())
    }

    @Test
    fun `extractTo requires empty target directory`() {
        val zip = createZip("file.txt" to "hello".toByteArray())
        val target = tempDir.resolve("non-empty-output")
        Files.createDirectories(target)
        Files.writeString(target.resolve("existing.txt"), "existing")

        assertFailsWith<ExtractionTargetException> {
            Simplifiles.archive(zip).extractTo(target)
        }

        assertEquals("existing", target.resolve("existing.txt").readText())
    }

    @Test
    fun `archive file can write and append text`() {
        val zip = createZip("file.txt" to "hello".toByteArray())

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            val file = archive.file("file.txt")
            file.appendText("\nworld")

            assertEquals("hello\nworld", file.readText())

            val newFile = archive.file("new/file.txt")
            newFile.writeText("created")

            assertEquals("created", newFile.readText())
        }
    }

    @Test
    fun `extracted archive supports realistic edit and repack workflow`() {
        val zip = createZip(
            "config/app.yml" to "name: demo\nmode: dev\n".toByteArray(),
            "docs/readme.txt" to "hello".toByteArray(),
            "logs/app.log" to "started".toByteArray(),
            "tmp/cache.bin" to byteArrayOf(1, 2, 3),
        )
        val repacked = tempDir.resolve("repacked.zip")

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            val config = archive.file("config/app.yml").readText()
            assertTrue(config.contains("mode: dev"))

            archive.file("logs/app.log").appendText("\nprocessed")
            archive.file("docs/readme.txt").copyTo("docs/readme-copy.txt")
            archive.file("tmp/cache.bin").moveTo("tmp/cache-old.bin")
            archive.file("reports/summary.txt").writeText("processed demo")

            val textFiles = archive.find("**/*.txt").map { it.path }.sorted()
            assertEquals(
                listOf(
                    "docs/readme-copy.txt",
                    "docs/readme.txt",
                    "reports/summary.txt",
                ),
                textFiles,
            )

            archive.saveAsZip(repacked)
        }

        Simplifiles.archive(repacked).extractToTemp().use { archive ->
            assertEquals("started\nprocessed", archive.file("logs/app.log").readText())
            assertEquals("hello", archive.file("docs/readme-copy.txt").readText())
            assertFalse(archive.file("tmp/cache.bin").exists)
            assertTrue(archive.file("tmp/cache-old.bin").exists)
            assertEquals("processed demo", archive.file("reports/summary.txt").readText())
        }
    }

    @Test
    fun `saveAsZip rejects existing output files`() {
        val zip = createZip("file.txt" to "hello".toByteArray())
        val output = tempDir.resolve("existing.zip")
        Files.writeString(output, "existing")

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            assertFailsWith<ArchiveWriteException> {
                archive.saveAsZip(output)
            }
        }

        assertEquals("existing", output.readText())
    }

    @Test
    fun `saveAsZip reports progress until completion`() {
        val zip = createZip("dir/file.txt" to "hello".toByteArray())
        val output = tempDir.resolve("progress-save.zip")
        val progressEvents = mutableListOf<ArchiveSaveProgress>()
        val options = ArchiveSaveOptions(
            progressListener = ArchiveSaveProgressListener { progress ->
                progressEvents += progress
            },
        )

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            archive.saveAsZip(output, options)
        }

        val finalProgress = progressEvents.last()
        assertTrue(output.exists())
        assertEquals(2, finalProgress.totalEntries)
        assertEquals(2, finalProgress.entriesProcessed)
        assertEquals(5, finalProgress.totalBytes)
        assertEquals(5, finalProgress.bytesWritten)
        assertTrue(finalProgress.isComplete)
    }

    @Test
    fun `saveAsZip uses configured buffer size`() {
        val zip = createZip("large.txt" to ByteArray(32) { it.toByte() })
        val output = tempDir.resolve("small-buffer-save.zip")
        val progressEvents = mutableListOf<ArchiveSaveProgress>()
        val options = ArchiveSaveOptions(
            progressListener = ArchiveSaveProgressListener { progress ->
                if (progress.currentEntryPath == "large.txt" && progress.bytesWritten > 0) {
                    progressEvents += progress
                }
            },
            bufferSize = 5,
        )

        Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxCompressionRatio = 10_000.0))
            .extractToTemp()
            .use { archive ->
                archive.saveAsZip(output, options)
            }

        assertTrue(progressEvents.size > 1)
        assertEquals(32, progressEvents.last().bytesWritten)
    }

    @Test
    fun `saveAsZip cancellation deletes partial output`() {
        val zip = createZip("large.txt" to ByteArray(128 * 1024) { it.toByte() })
        val output = tempDir.resolve("canceled-save.zip")
        val canceled = AtomicBoolean(false)
        val options = ArchiveSaveOptions(
            progressListener = ArchiveSaveProgressListener { progress ->
                if (progress.bytesWritten > 0) {
                    canceled.set(true)
                }
            },
            cancellationToken = CancellationToken.fromSupplier(canceled::get),
        )

        Simplifiles.archive(zip)
            .withPolicy(SecurityPolicy.strict().copy(maxCompressionRatio = 10_000.0))
            .extractToTemp()
            .use { archive ->
                assertFailsWith<ArchiveOperationCanceledException> {
                    archive.saveAsZip(output, options)
                }
            }

        assertFalse(output.exists())
    }

    @Test
    fun `pre canceled saveAsZip does not create output file`() {
        val zip = createZip("file.txt" to "hello".toByteArray())
        val output = tempDir.resolve("pre-canceled-save.zip")
        val options = ArchiveSaveOptions(
            cancellationToken = CancellationToken { true },
        )

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            assertFailsWith<ArchiveOperationCanceledException> {
                archive.saveAsZip(output, options)
            }
        }

        assertFalse(output.exists())
    }

    @Test
    fun `save options reject invalid buffer size`() {
        assertFailsWith<IllegalArgumentException> {
            ArchiveSaveOptions(bufferSize = 0)
        }
    }

    @Test
    fun `saveAsZip rejects output inside extracted archive root`() {
        val zip = createZip("file.txt" to "hello".toByteArray())

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            assertFailsWith<ArchiveWriteException> {
                archive.saveAsZip(archive.root.resolve("nested.zip"))
            }
        }
    }

    @Test
    fun `archive file rejects paths outside archive root`() {
        val zip = createZip("file.txt" to "hello".toByteArray())

        Simplifiles.archive(zip).extractToTemp().use { archive ->
            assertFailsWith<UnsafeArchivePathException> {
                archive.file("../outside.txt")
            }
        }
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
}
