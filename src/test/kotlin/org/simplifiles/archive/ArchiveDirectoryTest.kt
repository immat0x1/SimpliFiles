package org.simplifiles.archive

import org.junit.jupiter.api.io.TempDir
import org.simplifiles.SimpliFiles
import org.simplifiles.exception.ArchiveOperationException
import org.simplifiles.exception.UnsafeArchivePathException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchiveDirectoryTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracted archive exposes directory handles`() {
        val zip = createZip(
            "docs/readme.txt" to "readme".toByteArray(),
            "docs/guides/install.txt" to "install".toByteArray(),
            "assets/logo.txt" to "logo".toByteArray(),
        )

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            val docs = archive.directory("docs")

            assertTrue(docs.exists)
            assertEquals(
                listOf("assets", "docs", "docs/guides"),
                archive.directories.map { it.path }.sorted(),
            )
            assertEquals(listOf("docs/readme.txt"), docs.files.map { it.path })
            assertEquals(listOf("docs/guides"), docs.directories.map { it.path })
            assertEquals(
                listOf("docs/guides/install.txt", "docs/readme.txt"),
                docs.walkFiles().map { it.path }.sorted(),
            )
        }
    }

    @Test
    fun `directory can create copy move and delete recursively`() {
        val zip = createZip("input/seed.txt" to "seed".toByteArray())
        val repacked = tempDir.resolve("directories.zip")

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            archive.directory("reports/daily").create()
            archive.file("reports/daily/summary.txt").writeText("summary")
            archive.file("reports/daily/details.txt").writeText("details")

            val copied = archive.directory("reports").copyTo("backup/reports")
            assertEquals(
                listOf("backup/reports/daily/details.txt", "backup/reports/daily/summary.txt"),
                copied.walkFiles().map { it.path }.sorted(),
            )

            val moved = archive.directory("backup").moveTo("archive/backup")
            assertFalse(archive.directory("backup").exists)
            assertTrue(moved.exists)
            assertEquals("summary", archive.file("archive/backup/reports/daily/summary.txt").readText())

            assertTrue(archive.directory("reports").deleteRecursively())
            assertFalse(archive.directory("reports").exists)

            archive.saveAsZip(repacked)
        }

        SimpliFiles.archive(repacked).extractToTemp().use { archive ->
            assertFalse(archive.directory("reports").exists)
            assertEquals("summary", archive.file("archive/backup/reports/daily/summary.txt").readText())
            assertEquals("details", archive.file("archive/backup/reports/daily/details.txt").readText())
        }
    }

    @Test
    fun `saveAsZip preserves empty directories`() {
        val zip = createZip("input/seed.txt" to "seed".toByteArray())
        val repacked = tempDir.resolve("empty-directories.zip")

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            archive.directory("empty/reports").create()
            archive.saveAsZip(repacked)
        }

        SimpliFiles.archive(repacked).extractToTemp().use { archive ->
            assertTrue(archive.directory("empty").exists)
            assertTrue(archive.directory("empty/reports").exists)
            assertTrue(archive.directory("empty/reports").files.isEmpty())
        }
    }

    @Test
    fun `directory handle can represent missing directory`() {
        val zip = createZip("file.txt" to "hello".toByteArray())

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            val missing = archive.directory("missing")

            assertFalse(missing.exists)
            assertTrue(missing.files.isEmpty())
            assertTrue(missing.directories.isEmpty())
            assertTrue(missing.walkFiles().isEmpty())
            assertFalse(missing.deleteRecursively())
        }
    }

    @Test
    fun `directory rejects paths outside archive root`() {
        val zip = createZip("file.txt" to "hello".toByteArray())

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            assertFailsWith<UnsafeArchivePathException> {
                archive.directory("../outside")
            }
        }
    }

    @Test
    fun `directory copy and move reject target inside itself`() {
        val zip = createZip("docs/readme.txt" to "readme".toByteArray())

        SimpliFiles.archive(zip).extractToTemp().use { archive ->
            assertFailsWith<ArchiveOperationException> {
                archive.directory("docs").copyTo("docs/nested")
            }
            assertFailsWith<ArchiveOperationException> {
                archive.directory("docs").moveTo("docs/nested")
            }
        }
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): Path {
        val zip = tempDir.resolve("archive-${System.nanoTime()}.zip")

        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            for ((path, content) in entries) {
                output.putNextEntry(ZipEntry(path))
                output.write(content)
                output.closeEntry()
            }
        }

        assertTrue(zip.exists())
        return zip
    }
}
