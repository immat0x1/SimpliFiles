package org.simplifiles.files

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.FileOperationException
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimpliFileTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `file can read write append and delete text`() {
        val file = SimpliFiles.file(tempDir.resolve("config/app.json"))

        file.writeText("""{"enabled":true}""")
        file.appendText("\n")

        assertTrue(file.exists)
        assertEquals("json", file.extension)
        assertEquals("""{"enabled":true}""" + "\n", file.readText())

        assertTrue(file.delete())
        assertFalse(file.exists)
    }

    @Test
    fun `file exposes java file view`() {
        val javaFile = tempDir.resolve("config/app.json").toFile()
        val file = SimpliFiles.file(javaFile)

        assertEquals(javaFile.path, file.file.path)
        assertEquals(javaFile.path, file.toFile().path)
    }

    @Test
    fun `file can write atomically`() {
        val file = SimpliFiles.file(tempDir.resolve("metadata.json"))

        file.writeTextAtomic("""{"version":1}""")

        assertEquals("""{"version":1}""", file.readText())
    }

    @Test
    fun `file can read and write bytes`() {
        val file = SimpliFiles.file(tempDir.resolve("data/payload.bin"))
        val bytes = byteArrayOf(0, 1, 2, 3, 127)

        file.writeBytes(bytes)

        assertTrue(file.exists())
        assertEquals(bytes.size.toLong(), file.size)
        assertContentEquals(bytes, file.readBytes())
        assertContentEquals(bytes, file.readBytes(maxBytes = bytes.size.toLong()))
    }

    @Test
    fun `file streams create parent directories and replace content`() {
        val file = SimpliFiles.file(tempDir.resolve("streams/output.txt"))

        file.outputStream().use { output ->
            output.write("first".toByteArray())
        }
        file.outputStream().use { output ->
            output.write("second".toByteArray())
        }

        val text = file.inputStream().use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }

        assertEquals("second", text)
    }

    @Test
    fun `file can write from input streams`() {
        val file = SimpliFiles.file(tempDir.resolve("streams/output.txt"))

        val written = file.writeFrom(ByteArrayInputStream("payload".toByteArray()))

        assertEquals(file.path, written.path)
        assertEquals("payload", file.readText())
    }

    @Test
    fun `file stream writes enforce byte limits`() {
        val file = SimpliFiles.file(tempDir.resolve("streams/limited.txt"))
        file.writeText("old")

        assertFailsWith<FileOperationException> {
            file.writeFromAtomic(ByteArrayInputStream("payload".toByteArray()), maxBytes = 3)
        }

        assertEquals("old", file.readText())
    }

    @Test
    fun `file can touch marker files`() {
        val file = SimpliFiles.file(tempDir.resolve("markers/.ready"))

        val touched = file.touch()

        assertEquals(file.path, touched.path)
        assertTrue(file.exists)
        assertEquals(0, file.size)
    }

    @Test
    fun `file can read bounded lines`() {
        val file = SimpliFiles.file(tempDir.resolve("metadata.txt"))
        file.writeText("name: demo\nversion: 1\n")

        assertEquals(listOf("name: demo", "version: 1"), file.readLines(maxBytes = 32))

        val lines = mutableListOf<String>()
        file.forEachLine(maxBytes = 32) { line ->
            lines += line
        }
        assertEquals(listOf("name: demo", "version: 1"), lines)

        assertFailsWith<FileOperationException> {
            file.readLines(maxBytes = 4)
        }
    }

    @Test
    fun `file can write bytes atomically without explicit parent`() {
        val file = SimpliFiles.file("root-atomic-${System.nanoTime()}.bin")
        val bytes = byteArrayOf(9, 8, 7)

        try {
            file.writeBytesAtomic(bytes)

            assertContentEquals(bytes, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `file can copy and move`() {
        val source = SimpliFiles.file(tempDir.resolve("source.txt"))
        source.writeText("hello")

        val copied = source.copyTo(tempDir.resolve("nested/copied.txt"))
        assertEquals("hello", copied.readText())

        val moved = copied.moveTo(tempDir.resolve("moved.txt"))
        assertEquals("hello", moved.readText())
        assertFalse(tempDir.resolve("nested/copied.txt").exists())
    }

    @Test
    fun `file can enforce bounded reads`() {
        val file = SimpliFiles.file(tempDir.resolve("large.txt"))
        file.writeText("hello")

        assertEquals("hello", file.readText(maxBytes = 5))
        assertFailsWith<FileOperationException> {
            file.readBytes(maxBytes = 4)
        }
        assertFailsWith<IllegalArgumentException> {
            file.readBytes(maxBytes = -1)
        }
    }

    @Test
    fun `file copy and move support overwrite policies`() {
        val source = SimpliFiles.file(tempDir.resolve("source.txt"))
        source.writeText("new")
        val target = SimpliFiles.file(tempDir.resolve("target.txt"))
        target.writeText("old")

        assertFailsWith<FileOperationException> {
            source.copyTo(target.path, OverwritePolicy.ERROR)
        }

        source.copyTo(target.path, OverwritePolicy.SKIP)
        assertEquals("old", target.readText())

        source.copyTo(target.path, OverwritePolicy.REPLACE)
        assertEquals("new", target.readText())

        val moveSource = SimpliFiles.file(tempDir.resolve("move-source.txt"))
        moveSource.writeText("moved")
        moveSource.moveTo(target.path, OverwritePolicy.SKIP)
        assertTrue(moveSource.exists)
        assertEquals("new", target.readText())
    }
}
