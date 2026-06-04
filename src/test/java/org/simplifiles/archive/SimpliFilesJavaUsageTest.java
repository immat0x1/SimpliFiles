package org.simplifiles.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.simplifiles.SimpliFiles;
import org.simplifiles.archive.security.DuplicatePolicy;
import org.simplifiles.archive.security.SecurityPolicy;
import org.simplifiles.files.OverwritePolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpliFilesJavaUsageTest {
    @TempDir
    Path tempDir;

    @Test
    void javaUserCanValidateExtractEditAndSaveZip() throws Exception {
        Path zip = createZip("config/app.yml", "name: demo\n");
        Path output = tempDir.resolve("java-repacked.zip");
        SecurityPolicy policy = SecurityPolicy.builder()
                .maxEntries(100)
                .maxSingleFileSize(1_000_000)
                .duplicatePolicy(DuplicatePolicy.ERROR)
                .build();
        AtomicLong extractedEntries = new AtomicLong();
        AtomicLong savedEntries = new AtomicLong();
        ArchiveExtractionOptions options = ArchiveExtractionOptions.builder()
                .progressListener(progress -> extractedEntries.set(progress.getEntriesProcessed()))
                .bufferSize(16 * 1024)
                .build();
        ArchiveSaveOptions saveOptions = ArchiveSaveOptions.builder()
                .progressListener(progress -> savedEntries.set(progress.getEntriesProcessed()))
                .bufferSize(16 * 1024)
                .overwritePolicy(OverwritePolicy.ERROR)
                .compressionLevel(ArchiveSaveOptions.BEST_SPEED_LEVEL)
                .entryFilter(ArchiveEntryFilter.not(ArchiveEntryFilter.pathEndsWith(".tmp")))
                .build();

        ValidationReport report = SimpliFiles.archive(zip)
                .withPolicy(policy)
                .validate();

        assertTrue(report.isSafe());

        ArchiveExtractionPlan plan = SimpliFiles.archive(zip)
                .withPolicy(policy)
                .planExtractionTo(tempDir.resolve("planned-output"));

        assertTrue(plan.isSafe());
        assertEquals(1, plan.getTotalEntries());
        assertEquals("config/app.yml", plan.getEntries().get(0).getNormalizedPath());

        try (ExtractedArchive archive = SimpliFiles.archive(zip)
                .withPolicy(policy)
                .extractToTemp(options)) {
            ArchiveFile config = archive.file("config/app.yml");

            assertTrue(config.exists());
            assertEquals("name: demo\n", config.readText(StandardCharsets.UTF_8));

            archive.file("reports/summary.txt").writeText("ok", StandardCharsets.UTF_8);
            ArchiveDirectory reports = archive.directory("reports");
            assertTrue(reports.exists());
            assertEquals(1, reports.walkFiles().size());

            archive.saveAsZip(output, saveOptions);
        }

        assertEquals(1, extractedEntries.get());
        assertEquals(4, savedEntries.get());

        try (ExtractedArchive archive = SimpliFiles.archive(output).extractToTemp()) {
            assertEquals("ok", archive.file("reports/summary.txt").readText(StandardCharsets.UTF_8));
        }
    }

    private Path createZip(String path, String content) throws IOException {
        Path zip = tempDir.resolve("input.zip");

        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(path));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        return zip;
    }
}
