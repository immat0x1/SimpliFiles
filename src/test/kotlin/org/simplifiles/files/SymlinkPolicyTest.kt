package org.simplifiles.files

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.exception.FileOperationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymlinkPolicyTest {
    /**
     * Creates `<root>/src` holding `link.txt`, a symbolic link to a file outside that directory.
     */
    private fun linkedTree(root: Path): Path {
        val secret = root.resolve("outside.txt")
        Files.write(secret, "outside-content".toByteArray())
        val source = Files.createDirectories(root.resolve("src"))
        Files.write(source.resolve("inside.txt"), "inside-content".toByteArray())

        val supported = try {
            Files.createSymbolicLink(source.resolve("link.txt"), secret)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: java.io.IOException) {
            false
        }
        assumeTrue(supported, "filesystem does not support symbolic links")

        return source
    }

    private fun entryNames(archive: Path): List<String> =
        ZipFile(archive.toFile()).use { zip ->
            zip.entries().asSequence().map { it.name }.sorted().toList()
        }

    @Test
    fun `zipTo skips symbolic links by default`(@TempDir root: Path) {
        val source = linkedTree(root)
        val archive = root.resolve("out.zip")

        SimpliFiles.directory(source).zipTo(archive)

        assertContentEquals(listOf("inside.txt"), entryNames(archive))
    }

    @Test
    fun `zipTo fails on symbolic links under ERROR policy`(@TempDir root: Path) {
        val source = linkedTree(root)
        val options = org.simplifiles.archive.ArchiveSaveOptions.builder()
            .symlinkPolicy(SymlinkPolicy.ERROR)
            .build()

        assertFailsWith<ArchiveWriteException> {
            SimpliFiles.directory(source).zipTo(root.resolve("out.zip"), options)
        }
    }

    @Test
    fun `zipTo follows symbolic links under FOLLOW policy`(@TempDir root: Path) {
        val source = linkedTree(root)
        val archive = root.resolve("out.zip")
        val options = org.simplifiles.archive.ArchiveSaveOptions.builder()
            .symlinkPolicy(SymlinkPolicy.FOLLOW)
            .build()

        SimpliFiles.directory(source).zipTo(archive, options)

        assertContentEquals(listOf("inside.txt", "link.txt"), entryNames(archive))
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry("link.txt")
            assertEquals("outside-content", zip.getInputStream(entry).readBytes().decodeToString())
        }
    }

    @Test
    fun `pack skips symbolic links inside added directories`(@TempDir root: Path) {
        val source = linkedTree(root)
        val archive = root.resolve("pack.zip")

        SimpliFiles.pack()
            .addDirectory(source, "bundle")
            .zipTo(archive)

        assertContentEquals(listOf("bundle/", "bundle/inside.txt"), entryNames(archive))
    }

    @Test
    fun `copyTo skips symbolic links by default`(@TempDir root: Path) {
        val source = linkedTree(root)
        val target = root.resolve("dst")

        SimpliFiles.directory(source).copyTo(target, DirectoryTransferOptions.defaults())

        assertTrue(Files.exists(target.resolve("inside.txt")))
        assertFalse(Files.exists(target.resolve("link.txt")))
    }

    @Test
    fun `copyTo fails on symbolic links under ERROR policy`(@TempDir root: Path) {
        val source = linkedTree(root)
        val options = DirectoryTransferOptions.builder()
            .symlinkPolicy(SymlinkPolicy.ERROR)
            .build()

        assertFailsWith<FileOperationException> {
            SimpliFiles.directory(source).copyTo(root.resolve("dst"), options)
        }
    }

    @Test
    fun `copyTo follows symbolic links under FOLLOW policy`(@TempDir root: Path) {
        val source = linkedTree(root)
        val target = root.resolve("dst")
        val options = DirectoryTransferOptions.builder()
            .symlinkPolicy(SymlinkPolicy.FOLLOW)
            .build()

        SimpliFiles.directory(source).copyTo(target, options)

        assertEquals("outside-content", Files.readAllBytes(target.resolve("link.txt")).decodeToString())
    }
}
