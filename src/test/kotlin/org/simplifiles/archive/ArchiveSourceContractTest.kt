package org.simplifiles.archive

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.ArchiveOperationException
import org.simplifiles.internal.archive.ArchiveFormatDetector
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveSourceContractTest {
    /**
     * Hands out one byte per read, which a stream is allowed to do even when more is buffered.
     */
    private class DripFeedInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)

        override fun read(): Int = delegate.read()

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = if (length == 0) 0 else delegate.read(buffer, offset, 1)
    }

    @Test
    fun `signature reader fills the buffer across short reads`() {
        val zipSignature = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x14, 0x00)

        val signature = ArchiveFormatDetector.readSignature(DripFeedInputStream(zipSignature))

        assertContentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04), signature)
    }

    @Test
    fun `signature reader returns null when the stream ends early`() {
        assertNull(ArchiveFormatDetector.readSignature(DripFeedInputStream(byteArrayOf(0x50, 0x4b))))
    }

    @Test
    fun `inspect reports a missing archive through the library hierarchy`(@TempDir root: Path) {
        val exception = assertFailsWith<ArchiveOperationException> {
            SimpliFiles.archive(root.resolve("missing.zip")).inspect()
        }

        assertTrue(exception.message.orEmpty().contains("does not exist"))
    }

    @Test
    fun `inspect reports a directory through the library hierarchy`(@TempDir root: Path) {
        val directory = Files.createDirectories(root.resolve("directory"))

        val exception = assertFailsWith<ArchiveOperationException> {
            SimpliFiles.archive(directory).inspect()
        }

        assertTrue(exception.message.orEmpty().contains("not a regular file"))
    }

    @Test
    fun `validate reports a missing archive instead of throwing`(@TempDir root: Path) {
        val report = SimpliFiles.archive(root.resolve("missing.zip")).validate()

        assertFalse(report.isSafe)
        assertEquals(listOf("archive.unreadable"), report.issues.map { it.code })
        assertEquals(ArchiveIssueSeverity.BLOCKER, report.issues.single().severity)
    }

    @Test
    fun `validate reports a directory instead of throwing`(@TempDir root: Path) {
        val directory = Files.createDirectories(root.resolve("directory"))

        val report = SimpliFiles.archive(directory).validate()

        assertFalse(report.isSafe)
        assertEquals(listOf("archive.unreadable"), report.issues.map { it.code })
    }

    @Test
    fun `validate still reports unsupported formats`(@TempDir root: Path) {
        val notAnArchive = root.resolve("notes.txt")
        Files.write(notAnArchive, "plain text, definitely not a zip".toByteArray())

        val report = SimpliFiles.archive(notAnArchive).validate()

        assertEquals(listOf("archive.format.unsupported"), report.issues.map { it.code })
    }
}
