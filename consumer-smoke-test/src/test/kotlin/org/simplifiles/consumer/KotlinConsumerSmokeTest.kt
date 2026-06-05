package org.simplifiles.consumer

import org.simplifiles.SimpliFiles
import org.simplifiles.archive.security.SecurityPolicy
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinConsumerSmokeTest {
    @Test
    fun `kotlin consumer can use published artifact`() {
        val workspace = Files.createTempDirectory("simplifiles-kotlin-consumer-")
        val zip = workspace.resolve("input.zip")
        val output = workspace.resolve("output.zip")

        createZip(zip, "config/app.yml", "name: kotlin\n")

        SimpliFiles.archive(zip)
            .withPolicy(SecurityPolicy.strict())
            .extractToTemp()
            .use { archive ->
                val config = archive.file("config/app.yml").readText()
                archive.directory("reports").create()
                archive.file("reports/summary.txt").writeText(config)
                archive.zipTo(output)
            }

        SimpliFiles.archive(output).extractToTemp().use { archive ->
            assertEquals("name: kotlin\n", archive.file("reports/summary.txt").readText())
            assertTrue(archive.directory("reports").exists)
        }
    }

    private fun createZip(
        path: Path,
        entryName: String,
        content: String,
    ) {
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(content.toByteArray(Charsets.UTF_8))
            output.closeEntry()
        }
    }
}
