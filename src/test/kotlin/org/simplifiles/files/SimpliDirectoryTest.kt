package org.simplifiles.files

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.FileOperationException
import org.simplifiles.exception.UnsafePathException
import java.nio.file.Path
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

        root.file("metadata.json").writeTextAtomic("{}")
        root.directory("icons").create()
        root.file("icons/edit.svg").writeText("<svg/>")

        assertEquals(listOf("metadata.json"), root.files.map { it.path.fileName.toString() })
        assertEquals(listOf("icons"), root.directories.map { it.path.fileName.toString() })
        assertEquals(
            listOf("icons/edit.svg", "metadata.json"),
            root.walkFiles().map { tempDir.relativize(it.path).toString().replace('\\', '/') }.sorted(),
        )
    }

    @Test
    fun `directory rejects path traversal`() {
        val root = SimpliFiles.directory(tempDir)

        assertFailsWith<UnsafePathException> {
            root.file("../outside.txt")
        }
        assertFailsWith<UnsafePathException> {
            root.directory("/absolute")
        }
    }

    @Test
    fun `directory can copy move and delete recursively`() {
        val root = SimpliFiles.directory(tempDir.resolve("pack")).create()
        root.file("icons/edit.svg").writeText("<svg/>")

        val copied = root.copyTo(tempDir.resolve("pack-copy"))
        assertEquals("<svg/>", copied.file("icons/edit.svg").readText())

        val moved = copied.moveTo(tempDir.resolve("pack-moved"))
        assertEquals("<svg/>", moved.file("icons/edit.svg").readText())
        assertFalse(copied.exists)

        assertTrue(moved.deleteRecursively())
        assertFalse(moved.exists)
    }

    @Test
    fun `directory rejects copy and move into itself`() {
        val root = SimpliFiles.directory(tempDir.resolve("pack")).create()

        assertFailsWith<FileOperationException> {
            root.copyTo(tempDir.resolve("pack/nested"))
        }
        assertFailsWith<FileOperationException> {
            root.moveTo(tempDir.resolve("pack/nested"))
        }
    }
}
