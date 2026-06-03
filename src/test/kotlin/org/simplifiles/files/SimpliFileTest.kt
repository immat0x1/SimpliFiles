package org.simplifiles.files

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `file can write atomically`() {
        val file = SimpliFiles.file(tempDir.resolve("metadata.json"))

        file.writeTextAtomic("""{"version":1}""")

        assertEquals("""{"version":1}""", file.readText())
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
}
