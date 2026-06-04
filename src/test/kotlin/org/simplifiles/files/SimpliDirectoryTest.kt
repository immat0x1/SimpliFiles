package org.simplifiles.files

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.exception.FileOperationException
import org.simplifiles.exception.UnsafePathException
import java.io.File
import java.nio.file.Path
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
