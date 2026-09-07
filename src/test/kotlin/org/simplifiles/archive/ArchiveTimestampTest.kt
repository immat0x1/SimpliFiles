package org.simplifiles.archive

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveTimestampTest {
    private companion object {
        /** 2001-09-09T01:46:40Z, comfortably inside the DOS time range ZIP entries use. */
        const val SOURCE_MILLIS = 1_000_000_000_000L
        const val FIXED_MILLIS = 1_100_000_000_000L
    }

    private fun sourceTree(root: Path): Path {
        val source = Files.createDirectories(root.resolve("src/nested"))
        val file = source.resolve("a.txt")
        Files.write(file, "content".toByteArray())
        Files.setLastModifiedTime(file, FileTime.fromMillis(SOURCE_MILLIS))
        Files.setLastModifiedTime(source, FileTime.fromMillis(SOURCE_MILLIS))
        return root.resolve("src")
    }

    private fun entryTimes(archive: Path): Map<String, Long> =
        ZipFile(archive.toFile()).use { zip ->
            zip.entries().asSequence().associate { it.name to it.time }
        }

    @Test
    fun `entries keep the source modification time by default`(@TempDir root: Path) {
        val source = sourceTree(root)
        val archive = root.resolve("out.zip")

        SimpliFiles.directory(source).zipTo(archive)

        // ZIP's DOS timestamp has two-second granularity, so compare on that grid.
        assertEquals(SOURCE_MILLIS / 2000, entryTimes(archive).getValue("nested/a.txt") / 2000)
    }

    @Test
    fun `a fixed entry timestamp applies to files and directories`(@TempDir root: Path) {
        val source = sourceTree(root)
        val archive = root.resolve("out.zip")
        val options = ArchiveSaveOptions.builder()
            .entryTimestamp(FIXED_MILLIS)
            .build()

        SimpliFiles.directory(source).zipTo(archive, options)

        val times = entryTimes(archive)
        assertTrue(times.keys.containsAll(setOf("nested/", "nested/a.txt")))
        times.values.forEach { assertEquals(FIXED_MILLIS / 2000, it / 2000) }
    }

    @Test
    fun `a fixed entry timestamp makes repeated writes byte identical`(@TempDir root: Path) {
        val source = sourceTree(root)
        val options = ArchiveSaveOptions.builder()
            .entryTimestamp(FIXED_MILLIS)
            .build()
        val first = root.resolve("first.zip")
        val second = root.resolve("second.zip")

        SimpliFiles.directory(source).zipTo(first, options)
        SimpliFiles.directory(source).zipTo(second, options)

        assertTrue(Files.readAllBytes(first).contentEquals(Files.readAllBytes(second)))
    }

    @Test
    fun `pack entries honour a fixed timestamp`(@TempDir root: Path) {
        val source = sourceTree(root)
        val archive = root.resolve("pack.zip")
        val options = ArchiveSaveOptions.builder()
            .entryTimestamp(FIXED_MILLIS)
            .build()

        SimpliFiles.pack()
            .addDirectory(source, "bundle")
            .zipTo(archive, options)

        entryTimes(archive).values.forEach { assertEquals(FIXED_MILLIS / 2000, it / 2000) }
    }

    @Test
    fun `a negative entry timestamp other than the marker is rejected`() {
        val failure = runCatching { ArchiveSaveOptions(entryTimestamp = -2L) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `the preserve marker is the default`() {
        assertEquals(ArchiveSaveOptions.PRESERVE_SOURCE_TIMESTAMP, ArchiveSaveOptions.defaults().entryTimestamp)
        assertFalse(ArchiveSaveOptions.defaults().entryTimestamp >= 0)
    }
}
