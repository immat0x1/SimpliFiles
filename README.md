# SimpliFiles

Safe ZIP archive utilities for Java and Kotlin.

## Installation

Not published yet.

Planned Maven coordinate:

```kotlin
implementation("io.github.immat0x1:simplifiles:0.1.0")
```

## Requirements

- Java 17+
- Kotlin/JVM
- ZIP archives only

## Kotlin

```kotlin
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.security.SecurityPolicy

SimpliFiles.archive("app.zip")
    .withPolicy(SecurityPolicy.strict())
    .extractToTemp()
    .use { archive ->
        val config = archive.file("config/app.yml").readText()

        archive.file("logs/app.log").appendText("\nprocessed")
        archive.file("reports/summary.txt").writeText(config)
        archive.directory("reports").copyTo("backup/reports")
        archive.find("**/*.tmp").forEach { it.delete() }
        archive.saveAsZip("cleaned.zip")
    }
```

## Java

```java
import org.simplifiles.SimpliFiles;
import org.simplifiles.archive.ArchiveExtractionOptions;
import org.simplifiles.archive.ArchiveExtractionPlan;
import org.simplifiles.archive.ArchiveFile;
import org.simplifiles.archive.ArchiveSaveOptions;
import org.simplifiles.archive.ExtractedArchive;
import org.simplifiles.archive.ValidationReport;
import org.simplifiles.archive.security.DuplicatePolicy;
import org.simplifiles.archive.security.SecurityPolicy;

import java.nio.charset.StandardCharsets;

SecurityPolicy policy = SecurityPolicy.builder()
        .maxEntries(10_000)
        .duplicatePolicy(DuplicatePolicy.ERROR)
        .build();
ArchiveExtractionOptions options = ArchiveExtractionOptions.builder()
        .progressListener(progress -> {
            long processed = progress.getEntriesProcessed();
            long total = progress.getTotalEntries();
        })
        .bufferSize(64 * 1024)
        .build();
ArchiveSaveOptions saveOptions = ArchiveSaveOptions.builder()
        .bufferSize(64 * 1024)
        .build();

ValidationReport report = SimpliFiles.archive("app.zip")
        .withPolicy(policy)
        .validate();

if (report.isSafe()) {
    ArchiveExtractionPlan plan = SimpliFiles.archive("app.zip")
            .withPolicy(policy)
            .planExtractionTo("output");

    try (ExtractedArchive archive = SimpliFiles.archive("app.zip")
            .withPolicy(policy)
            .extractToTemp(options)) {
        ArchiveFile config = archive.file("config/app.yml");
        String text = config.readText(StandardCharsets.UTF_8);

        archive.file("reports/summary.txt").writeText(text, StandardCharsets.UTF_8);
        archive.saveAsZip("cleaned.zip", saveOptions);
    }
}
```

## Features

- ZIP inspection without extraction
- ZIP validation report
- Dry-run extraction plans
- Safe extraction to a new or empty directory
- Temporary extraction with cleanup on close
- Extraction progress callbacks
- Extraction cancellation tokens
- Configurable extraction buffer size
- Save progress callbacks, cancellation tokens, and buffer size
- File APIs: read, write, append, copy, move, delete
- Directory APIs: create, list, copy, move, recursive delete
- Glob search for extracted files
- Save modified extracted contents back to ZIP

## Security Defaults

`SecurityPolicy.strict()` is the default.

It rejects or limits:

- path traversal
- absolute paths
- Windows absolute paths
- duplicate paths
- file/directory path conflicts
- unsupported compression methods
- suspicious compression ratio
- maximum entry count
- maximum single file size
- maximum total uncompressed size

## Errors

Core exception types:

- `ArchiveValidationException`
- `UnsafeArchivePathException`
- `ExtractionTargetException`
- `ArchiveWriteException`
- `ArchiveOperationException`
- `ArchiveOperationCanceledException`
- `UnsupportedArchiveFormatException`
- `CorruptedArchiveException`

## Limitations

- ZIP only
- symlink and hardlink handling is not complete yet
- benchmark coverage is basic

## License

Apache License 2.0. See [LICENSE](LICENSE).
