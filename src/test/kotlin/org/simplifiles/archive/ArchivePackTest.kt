package org.simplifiles.archive

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.exception.UnsafeArchivePathException
import org.simplifiles.files.OverwritePolicy
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchivePackTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `pack creates zip from independent files and directories`() {
        val readme = tempDir.resolve("README.md")
        Files.write(readme, "readme".toByteArray())
        val assets = SimpliFiles.directory(tempDir.resolve("assets")).create()
        assets.file("logo.txt").writeText("logo")
        assets.directory("empty").create()
        val output = tempDir.resolve("bundle.zip")

        val archive = SimpliFiles.pack()
            .addFile(readme, "docs/README.md")
            .addDirectory(assets.path, "public/assets")
            .zipTo(output)

        assertTrue(archive.exists)
        ZipFile(archive.file).use { zip ->
            assertEquals("readme", zip.readText("docs/README.md"))
            assertEquals("logo", zip.readText("public/assets/logo.txt"))
            assertTrue(zip.getEntry("public/assets/empty/").isDirectory)
        }
    }

    @Test
    fun `pack supports save options`() {
        val source = tempDir.resolve("notes.txt")
        Files.write(source, "notes".toByteArray())
        val output = tempDir.resolve("bundle.zip")
        Files.write(output, "old".toByteArray())
        val options = ArchiveSaveOptions.builder()
            .overwritePolicy(OverwritePolicy.REPLACE)
            .entryFilter(ArchiveEntryFilter.pathStartsWith("docs/"))
            .build()

        SimpliFiles.pack()
            .addFile(source, "docs/notes.txt")
            .addFile(source, "tmp/notes.txt")
            .zipTo(output, options)

        ZipFile(output.toFile()).use { zip ->
            assertEquals("notes", zip.readText("docs/notes.txt"))
            assertEquals(null, zip.getEntry("tmp/notes.txt"))
        }
    }

    @Test
    fun `pack supports overwrite policy shortcut`() {
        val source = tempDir.resolve("notes.txt")
        Files.write(source, "notes".toByteArray())
        val output = tempDir.resolve("bundle.zip")
        Files.write(output, "old".toByteArray())

        SimpliFiles.pack()
            .addFile(source, "docs/notes.txt")
            .zipTo(output, OverwritePolicy.REPLACE)

        ZipFile(output.toFile()).use { zip ->
            assertEquals("notes", zip.readText("docs/notes.txt"))
        }
    }

    @Test
    fun `pack rejects unsafe entry paths`() {
        val source = tempDir.resolve("notes.txt")
        Files.write(source, "notes".toByteArray())

        assertFailsWith<UnsafeArchivePathException> {
            SimpliFiles.pack().addFile(source, "../notes.txt")
        }
        assertFailsWith<UnsafeArchivePathException> {
            SimpliFiles.pack().addFile(source, "/notes.txt")
        }
    }

    @Test
    fun `pack rejects output inside source directory`() {
        val assets = SimpliFiles.directory(tempDir.resolve("assets")).create()
        assets.file("logo.txt").writeText("logo")

        assertFailsWith<ArchiveWriteException> {
            SimpliFiles.pack()
                .addDirectory(assets.path, "assets")
                .zipTo(assets.file("bundle.zip").path)
        }
    }

    @Test
    fun `pack rejects conflicting entry paths`() {
        val source = tempDir.resolve("source.txt")
        Files.write(source, "source".toByteArray())
        val output = tempDir.resolve("bundle.zip")

        assertFailsWith<ArchiveWriteException> {
            SimpliFiles.pack()
                .addFile(source, "assets")
                .addFile(source, "assets/logo.txt")
                .zipTo(output)
        }

        assertFalse(output.toFile().exists())
    }

    private fun ZipFile.readText(path: String): String =
        getInputStream(getEntry(path)).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
}
