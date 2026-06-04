package org.simplifiles.files

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.ArchiveEntryFilter
import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.archive.ArchiveSaveProgress
import org.simplifiles.archive.ArchiveSaveProgressListener
import org.simplifiles.archive.CancellationToken
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.exception.FileOperationException
import org.simplifiles.exception.UnsafePathException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpliDirectoryTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `directory resolves children safely`() {
        val root = SimpliFiles.directory(tempDir).create()

        root.file("config.json").writeTextAtomic("{}")
        root.directory("reports").create()
        root.file("reports/summary.txt").writeText("summary")

        assertEquals(tempDir.resolve("reports/summary.txt"), root.resolveInside("reports/summary.txt"))
        assertEquals(listOf("config.json"), root.files.map { it.path.fileName.toString() })
        assertEquals(listOf("reports"), root.directories.map { it.path.fileName.toString() })
        assertEquals(
            listOf("config.json", "reports/summary.txt"),
            root.walkFiles().map { tempDir.relativize(it.path).toString().replace('\\', '/') }.sorted(),
        )
    }

    @Test
    fun `directory exposes java file view`() {
        val javaDirectory = tempDir.resolve("workspace").toFile()
        val directory = SimpliFiles.directory(javaDirectory)

        assertEquals(javaDirectory.path, directory.file.path)
        assertEquals(javaDirectory.path, directory.toFile().path)
        assertEquals(File(javaDirectory, "config.json").path, directory.file("config.json").file.path)
    }

    @Test
    fun `directory rejects path traversal`() {
        val root = SimpliFiles.directory(tempDir)

        assertFailsWith<UnsafePathException> {
            root.file("../outside.txt")
        }
        assertFailsWith<UnsafePathException> {
            root.file("reports/../config.json")
        }
        assertFailsWith<UnsafePathException> {
            root.directory("/absolute")
        }
        assertFailsWith<UnsafePathException> {
            root.directory("C:\\absolute")
        }
    }

    @Test
    fun `directory can copy move and delete recursively`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("reports/summary.txt").writeText("summary")

        val copied = root.copyTo(tempDir.resolve("workspace-copy"))
        assertEquals("summary", copied.file("reports/summary.txt").readText())

        val moved = copied.moveTo(tempDir.resolve("workspace-moved"))
        assertEquals("summary", moved.file("reports/summary.txt").readText())
        assertFalse(copied.exists)

        assertTrue(moved.deleteRecursively())
        assertFalse(moved.exists)
    }

    @Test
    fun `directory can be zipped`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("config.json").writeText("""{"enabled":true}""")
        root.file("reports/summary.txt").writeText("summary")
        root.directory("empty").create()

        val output = root.zipTo(tempDir.resolve("workspace.zip"))

        assertTrue(output.exists)
        ZipFile(output.file).use { zip ->
            assertEquals("""{"enabled":true}""", zip.readText("config.json"))
            assertEquals("summary", zip.readText("reports/summary.txt"))
            assertTrue(zip.getEntry("empty/").isDirectory)
        }
    }

    @Test
    fun `directory zip rejects output inside source directory`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("config.json").writeText("{}")

        assertFailsWith<ArchiveWriteException> {
            root.zipTo(root.file("nested.zip").file)
        }
    }

    @Test
    fun `directory zip supports save options`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("reports/summary.txt").writeText("summary")
        root.file("reports/debug.tmp").writeText("debug")
        root.file("assets/logo.txt").writeText("logo")
        val output = tempDir.resolve("workspace.zip")
        Files.write(output, "old".toByteArray())

        val options = ArchiveSaveOptions.builder()
            .overwritePolicy(OverwritePolicy.REPLACE)
            .compressionLevel(ArchiveSaveOptions.NO_COMPRESSION_LEVEL)
            .entryFilter { path -> !path.endsWith(".tmp") }
            .build()

        root.zipTo(output, options)

        ZipFile(output.toFile()).use { zip ->
            assertEquals("summary", zip.readText("reports/summary.txt"))
            assertEquals("logo", zip.readText("assets/logo.txt"))
            assertEquals(null, zip.getEntry("reports/debug.tmp"))
        }
    }

    @Test
    fun `directory zip can skip existing output`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("config.json").writeText("{}")
        val output = tempDir.resolve("workspace.zip")
        Files.write(output, "old".toByteArray())

        val options = ArchiveSaveOptions.builder()
            .overwritePolicy(OverwritePolicy.SKIP)
            .build()

        root.zipTo(output, options)

        assertEquals("old", Files.readAllBytes(output).toString(Charsets.UTF_8))
    }

    @Test
    fun `directory zip reports progress until completion`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.directory("empty").create()
        root.file("reports/summary.txt").writeText("summary")
        val output = tempDir.resolve("workspace.zip")
        val progressEvents = mutableListOf<ArchiveSaveProgress>()
        val options = ArchiveSaveOptions.builder()
            .progressListener(ArchiveSaveProgressListener { progress ->
                progressEvents += progress
            })
            .build()

        root.zipTo(output, options)

        val finalProgress = progressEvents.last()
        assertTrue(output.toFile().exists())
        assertEquals(3, finalProgress.totalEntries)
        assertEquals(3, finalProgress.entriesProcessed)
        assertEquals(7, finalProgress.totalBytes)
        assertEquals(7, finalProgress.bytesWritten)
        assertTrue(finalProgress.isComplete)
    }

    @Test
    fun `directory zip uses configured buffer size`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("large.txt").writeBytes(ByteArray(32) { it.toByte() })
        val output = tempDir.resolve("workspace.zip")
        val progressEvents = mutableListOf<ArchiveSaveProgress>()
        val options = ArchiveSaveOptions.builder()
            .bufferSize(5)
            .progressListener(ArchiveSaveProgressListener { progress ->
                if (progress.currentEntryPath == "large.txt" && progress.bytesWritten > 0) {
                    progressEvents += progress
                }
            })
            .build()

        root.zipTo(output, options)

        assertTrue(progressEvents.size > 1)
        assertEquals(32, progressEvents.last().bytesWritten)
    }

    @Test
    fun `directory zip cancellation deletes partial output`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("large.txt").writeBytes(ByteArray(128 * 1024) { it.toByte() })
        val output = tempDir.resolve("workspace.zip")
        val canceled = AtomicBoolean(false)
        val options = ArchiveSaveOptions.builder()
            .progressListener(ArchiveSaveProgressListener { progress ->
                if (progress.bytesWritten > 0) {
                    canceled.set(true)
                }
            })
            .cancellationToken(CancellationToken.fromSupplier(canceled::get))
            .build()

        assertFailsWith<ArchiveOperationCanceledException> {
            root.zipTo(output, options)
        }

        assertFalse(output.toFile().exists())
    }

    @Test
    fun `pre canceled directory zip replace preserves existing output`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("config.json").writeText("{}")
        val output = tempDir.resolve("workspace.zip")
        Files.write(output, "existing".toByteArray())
        val options = ArchiveSaveOptions.builder()
            .overwritePolicy(OverwritePolicy.REPLACE)
            .cancellationToken(CancellationToken { true })
            .build()

        assertFailsWith<ArchiveOperationCanceledException> {
            root.zipTo(output, options)
        }

        assertEquals("existing", Files.readAllBytes(output).toString(Charsets.UTF_8))
    }

    @Test
    fun `directory zip can write empty archive after filtering all entries`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.directory("empty").create()
        root.file("config.json").writeText("{}")
        val output = tempDir.resolve("workspace.zip")
        val options = ArchiveSaveOptions.builder()
            .entryFilter(ArchiveEntryFilter.excludeAll())
            .build()

        root.zipTo(output, options)

        ZipFile(output.toFile()).use { zip ->
            assertEquals(0, zip.size())
        }
    }

    @Test
    fun `directory zip entry filter helpers can exclude path groups`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("reports/summary.txt").writeText("summary")
        root.file("tmp/cache.bin").writeText("cache")
        root.file("logs/app.log").writeText("log")
        val output = tempDir.resolve("workspace.zip")
        val options = ArchiveSaveOptions.builder()
            .entryFilter(
                ArchiveEntryFilter.allOf(
                    ArchiveEntryFilter.not(ArchiveEntryFilter.pathStartsWith("tmp/")),
                    ArchiveEntryFilter.not(ArchiveEntryFilter.pathEndsWith(".log")),
                ),
            )
            .build()

        root.zipTo(output, options)

        ZipFile(output.toFile()).use { zip ->
            assertEquals("summary", zip.readText("reports/summary.txt"))
            assertEquals(null, zip.getEntry("tmp/cache.bin"))
            assertEquals(null, zip.getEntry("logs/app.log"))
        }
    }

    @Test
    fun `directory zip filter failure preserves existing output`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        root.file("config.json").writeText("{}")
        val output = tempDir.resolve("workspace.zip")
        Files.write(output, "existing".toByteArray())
        val options = ArchiveSaveOptions.builder()
            .overwritePolicy(OverwritePolicy.REPLACE)
            .entryFilter {
                throw IllegalStateException("filter failed")
            }
            .build()

        assertFailsWith<IllegalStateException> {
            root.zipTo(output, options)
        }

        assertEquals("existing", Files.readAllBytes(output).toString(Charsets.UTF_8))
    }

    @Test
    fun `directory zip handles many small files`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        val fileCount = 300
        var totalBytes = 0L
        repeat(fileCount) { index ->
            val content = "item-$index"
            totalBytes += content.toByteArray().size
            root.file("items/group-${index % 10}/item-$index.txt").writeText(content)
        }
        val output = tempDir.resolve("many-small-files.zip")
        val progressEvents = mutableListOf<ArchiveSaveProgress>()
        val options = ArchiveSaveOptions.builder()
            .progressListener(ArchiveSaveProgressListener { progress ->
                progressEvents += progress
            })
            .build()

        root.zipTo(output, options)

        val finalProgress = progressEvents.last()
        assertEquals(totalBytes, finalProgress.totalBytes)
        assertEquals(totalBytes, finalProgress.bytesWritten)
        assertTrue(finalProgress.totalEntries >= fileCount)
        assertTrue(finalProgress.isComplete)

        ZipFile(output.toFile()).use { zip ->
            assertEquals("item-0", zip.readText("items/group-0/item-0.txt"))
            assertEquals("item-299", zip.readText("items/group-9/item-299.txt"))
        }
    }

    @Test
    fun `directory zip handles larger files without loading them into memory at once`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()
        val bytes = ByteArray(1024 * 1024) { index -> (index % 251).toByte() }
        root.file("payload.bin").writeBytes(bytes)
        val output = tempDir.resolve("large-file.zip")
        val progressEvents = mutableListOf<ArchiveSaveProgress>()
        val options = ArchiveSaveOptions.builder()
            .bufferSize(8 * 1024)
            .progressListener(ArchiveSaveProgressListener { progress ->
                if (progress.currentEntryPath == "payload.bin") {
                    progressEvents += progress
                }
            })
            .build()

        root.zipTo(output, options)

        assertTrue(progressEvents.size > 8)
        assertEquals(bytes.size.toLong(), progressEvents.last().bytesWritten)
        ZipFile(output.toFile()).use { zip ->
            val extracted = zip.getInputStream(zip.getEntry("payload.bin")).use { input ->
                input.readBytes()
            }
            assertEquals(bytes.size, extracted.size)
            assertEquals(bytes.first(), extracted.first())
            assertEquals(bytes.last(), extracted.last())
        }
    }

    @Test
    fun `directory copy and move support overwrite policies`() {
        val source = SimpliFiles.directory(tempDir.resolve("source")).create()
        source.file("file.txt").writeText("new")
        val target = SimpliFiles.directory(tempDir.resolve("target")).create()
        target.file("file.txt").writeText("old")
        target.file("stale.txt").writeText("stale")

        assertFailsWith<FileOperationException> {
            source.copyTo(target.path, OverwritePolicy.ERROR)
        }

        source.copyTo(target.path, OverwritePolicy.SKIP)
        assertEquals("old", target.file("file.txt").readText())

        source.copyTo(target.path, OverwritePolicy.REPLACE)
        assertEquals("new", target.file("file.txt").readText())
        assertFalse(target.file("stale.txt").exists)

        val moveSource = SimpliFiles.directory(tempDir.resolve("move-source")).create()
        moveSource.file("file.txt").writeText("moved")
        moveSource.moveTo(target.path, OverwritePolicy.SKIP)
        assertTrue(moveSource.exists)
        assertEquals("new", target.file("file.txt").readText())
    }

    @Test
    fun `directory rejects copy and move into itself`() {
        val root = SimpliFiles.directory(tempDir.resolve("workspace")).create()

        assertFailsWith<FileOperationException> {
            root.copyTo(tempDir.resolve("workspace/nested"))
        }
        assertFailsWith<FileOperationException> {
            root.moveTo(tempDir.resolve("workspace/nested"))
        }
    }

    private fun ZipFile.readText(path: String): String =
        getInputStream(getEntry(path)).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
}
