package org.simplifiles.consumer;

import org.junit.jupiter.api.Test;
import org.simplifiles.SimpliFiles;
import org.simplifiles.archive.ArchiveFile;
import org.simplifiles.archive.ExtractedArchive;
import org.simplifiles.archive.ValidationReport;
import org.simplifiles.archive.security.DuplicatePolicy;
import org.simplifiles.archive.security.SecurityPolicy;
import org.simplifiles.exception.UnsafeArchivePathException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaConsumerSmokeTest {
    @Test
    void javaConsumerCanUsePublishedArtifact() throws Exception {
        Path workspace = Files.createTempDirectory("simplifiles-java-consumer-");
        Path zip = workspace.resolve("input.zip");
        Path output = workspace.resolve("output.zip");

        createZip(zip, "config/app.yml", "name: java\n");

        SecurityPolicy policy = SecurityPolicy.builder()
                .maxEntries(100)
                .duplicatePolicy(DuplicatePolicy.ERROR)
                .build();

        ValidationReport report = SimpliFiles.archive(zip)
                .withPolicy(policy)
                .validate();

        assertTrue(report.isSafe());

        try (ExtractedArchive archive = SimpliFiles.archive(zip)
                .withPolicy(policy)
                .extractToTemp()) {
            ArchiveFile config = archive.file("config/app.yml");
            String text = config.readText(StandardCharsets.UTF_8);

            archive.directory("reports").create();
            archive.file("reports/summary.txt").writeText(text, StandardCharsets.UTF_8);
            assertThrows(UnsafeArchivePathException.class, () -> archive.file("../outside.txt"));
            archive.zipTo(output);
        }

        try (ExtractedArchive archive = SimpliFiles.archive(output).extractToTemp()) {
            assertEquals("name: java\n", archive.file("reports/summary.txt").readText(StandardCharsets.UTF_8));
            assertTrue(archive.directory("reports").exists());
        }
    }

    private void createZip(Path path, String entryName, String content) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
