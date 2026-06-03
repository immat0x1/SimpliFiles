package org.simplifiles.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.simplifiles.Simplifiles;
import org.simplifiles.archive.ArchiveFile;
import org.simplifiles.archive.ExtractedArchive;
import org.simplifiles.archive.security.SecurityPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(2)
public class ZipArchiveBenchmark {
    private static final int BUFFER_SIZE = 64 * 1024;

    @Benchmark
    public int simplifilesInspect(ArchiveState state) {
        return Simplifiles.archive(state.archive).inspect().getEntryCount();
    }

    @Benchmark
    public boolean simplifilesValidate(ArchiveState state) {
        return Simplifiles.archive(state.archive)
            .withPolicy(state.policy)
            .validate()
            .isSafe();
    }

    @Benchmark
    public long simplifilesExtractToTemp(ArchiveState state, Blackhole blackhole) {
        try (ExtractedArchive archive = Simplifiles.archive(state.archive)
            .withPolicy(state.policy)
            .extractToTemp()) {
            List<ArchiveFile> files = archive.getFiles();
            blackhole.consume(files);
            return totalSimplifilesSize(files);
        }
    }

    @Benchmark
    public int javaZipFileInspect(ArchiveState state) throws IOException {
        int entries = 0;
        try (ZipFile zipFile = new ZipFile(state.archive.toFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                entries += entry.getName().isEmpty() ? 0 : 1;
            }
        }
        return entries;
    }

    @Benchmark
    public long javaZipFileExtractToTemp(ArchiveState state) throws IOException {
        Path target = Files.createTempDirectory("simplifiles-jmh-baseline-");
        try {
            extractWithJavaZipFile(state.archive, target);
            return totalRegularFileSize(target);
        } finally {
            deleteRecursively(target);
        }
    }

    @State(Scope.Benchmark)
    public static class ArchiveState {
        @Param({"small", "manySmall", "mixed"})
        public String scenario;

        Path workDir;
        Path archive;
        SecurityPolicy policy;

        @Setup
        public void setup() throws IOException {
            workDir = Files.createTempDirectory("simplifiles-jmh-");
            archive = workDir.resolve(scenario + ".zip");
            createArchive(archive, scenario);
            policy = SecurityPolicy.builder()
                .maxEntries(100_000)
                .maxSingleFileSize(128L * 1024 * 1024)
                .maxTotalUncompressedSize(512L * 1024 * 1024)
                .maxCompressionRatio(10_000.0)
                .build();
        }

        @TearDown
        public void tearDown() throws IOException {
            deleteRecursively(workDir);
        }
    }

    private static void createArchive(Path archive, String scenario) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            switch (scenario) {
                case "small" -> writeFiles(zip, "small", 32, 1_024);
                case "manySmall" -> writeFiles(zip, "many-small", 1_000, 128);
                case "mixed" -> {
                    writeFiles(zip, "mixed/docs", 64, 16 * 1024);
                    writeFiles(zip, "mixed/assets", 4, 1024 * 1024);
                }
                default -> throw new IllegalArgumentException("Unknown benchmark scenario: " + scenario);
            }
        }
    }

    private static void writeFiles(
        ZipOutputStream zip,
        String directory,
        int fileCount,
        int fileSize
    ) throws IOException {
        ZipEntry directoryEntry = new ZipEntry(directory + "/");
        zip.putNextEntry(directoryEntry);
        zip.closeEntry();

        for (int index = 0; index < fileCount; index++) {
            String path = directory + "/file-" + index + ".bin";
            zip.putNextEntry(new ZipEntry(path));
            zip.write(payload(fileSize, index));
            zip.closeEntry();
        }
    }

    private static byte[] payload(int size, int seed) {
        byte[] bytes = new byte[size];
        int value = seed * 17 + 31;
        for (int index = 0; index < size; index++) {
            value = value * 1_103_515_245 + 12_345;
            bytes[index] = (byte) (value >>> 16);
        }
        return bytes;
    }

    private static void extractWithJavaZipFile(Path archive, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path output = resolveArchiveEntry(root, entry);
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }

                Files.createDirectories(output.getParent());
                try (
                    InputStream input = zipFile.getInputStream(entry);
                    OutputStream outputStream = Files.newOutputStream(output)
                ) {
                    input.transferTo(outputStream);
                }
            }
        }
    }

    private static Path resolveArchiveEntry(Path root, ZipEntry entry) throws IOException {
        String name = entry.getName();
        if (name.isEmpty() || name.indexOf('\\') >= 0) {
            throw new IOException("Unsafe ZIP entry name: " + name);
        }

        Path output = root.resolve(name).normalize();
        if (!output.startsWith(root) || output.isAbsolute() && !output.startsWith(root)) {
            throw new IOException("Unsafe ZIP entry path: " + name);
        }
        return output;
    }

    private static long totalSimplifilesSize(List<ArchiveFile> files) {
        long total = 0;
        for (ArchiveFile file : files) {
            total += file.getSize();
        }
        return total;
    }

    private static long totalRegularFileSize(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .sum();
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            List<Path> sorted = paths
                .sorted(Comparator.reverseOrder())
                .toList();
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }
}
