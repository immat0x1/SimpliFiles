package org.simplifiles.files

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.security.SecurityPolicy
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HandleValueSemanticsTest {
    @Test
    fun `file handles for the same path are equal`(@TempDir root: Path) {
        val first = SimpliFiles.file(root.resolve("a.txt"))
        val second = SimpliFiles.file(root.resolve("a.txt"))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, SimpliFiles.file(root.resolve("b.txt")))
    }

    @Test
    fun `directory handles for the same path are equal`(@TempDir root: Path) {
        assertEquals(SimpliFiles.directory(root.resolve("d")), SimpliFiles.directory(root.resolve("d")))
        assertNotEquals(SimpliFiles.directory(root.resolve("d")), SimpliFiles.directory(root.resolve("e")))
    }

    @Test
    fun `archive sources compare path and policy`(@TempDir root: Path) {
        val archive = root.resolve("a.zip")
        val relaxed = SecurityPolicy.builder().maxEntries(5).build()

        assertEquals(SimpliFiles.archive(archive), SimpliFiles.archive(archive))
        assertNotEquals(SimpliFiles.archive(archive), SimpliFiles.archive(archive).withPolicy(relaxed))
    }

    @Test
    fun `handles can be used as set and map keys`(@TempDir root: Path) {
        val handles = setOf(
            SimpliFiles.file(root.resolve("a.txt")),
            SimpliFiles.file(root.resolve("a.txt")),
            SimpliFiles.file(root.resolve("b.txt")),
        )

        assertEquals(2, handles.size)
    }

    @Test
    fun `toString names the type and the path`(@TempDir root: Path) {
        val rendered = SimpliFiles.file(root.resolve("a.txt")).toString()

        assertTrue(rendered.startsWith("SimpliFile(path="), rendered)
        assertTrue(rendered.contains("a.txt"), rendered)
        assertTrue(SimpliFiles.directory(root).toString().startsWith("SimpliDirectory(path="))
    }

    @Test
    fun `options render their values`() {
        assertTrue(DirectoryTransferOptions.defaults().toString().contains("symlinkPolicy=SKIP"))
    }
}
