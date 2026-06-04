<p align="center">
  <img src="assets/simplifiles.png" alt="SimpliFiles logo" width="128" height="128">
</p>

<h1 align="center">SimpliFiles</h1>

<p align="center">
  Safe and convenient file toolkit for Java and Kotlin, with archive-first APIs.
</p>

<p align="center">
  <a href="https://github.com/immat0x1/SimpliFiles/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/immat0x1/SimpliFiles/ci.yml?branch=main&style=flat-square"></a>
  <a href="https://central.sonatype.com/artifact/io.github.immat0x1/simplifiles"><img alt="Snapshot" src="https://img.shields.io/badge/snapshot-0.1.4--SNAPSHOT-1684ff?style=flat-square"></a>
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-f89820?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-JVM-7f52ff?style=flat-square">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/License-Apache--2.0-green?style=flat-square"></a>
</p>

## Installation

Snapshot builds are available from Maven Central Snapshots:

```kotlin
repositories {
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        content {
            includeModule("io.github.immat0x1", "simplifiles")
        }
    }
}

dependencies {
    implementation("io.github.immat0x1:simplifiles:0.1.4-SNAPSHOT")
}
```

Latest stable coordinate:

```kotlin
implementation("io.github.immat0x1:simplifiles:0.1.3")
```

## Requirements

- Runtime: Java 8+ API surface
- Build: Java 17 toolchain
- Kotlin/JVM
- Archive module currently supports ZIP

## Why SimpliFiles

Working with files and archives in Java/Kotlin is powerful, but the safe version of simple tasks often turns into repetitive infrastructure code. Extracting a ZIP means validating paths, creating directories, copying streams, handling duplicate entries, checking sizes, cleaning partial output, and only then reading the files you actually needed.

SimpliFiles was created to make those workflows short by default and safer by default.

| Common pain | Standard API reality | SimpliFiles approach |
| --- | --- | --- |
| Extracting archives takes too much boilerplate | You manually iterate entries, normalize paths, create directories, copy streams, and close resources | `SimpliFiles.archive(path).extractTo(...)` validates and extracts with a focused API |
| Archive extraction is easy to make unsafe | Zip Slip, absolute paths, duplicate paths, oversized entries, and zip bombs are easy to miss | Strict `SecurityPolicy` is applied before extraction and while bytes are written |
| You only get files on disk after extraction | The caller has to rebuild convenience helpers around the output directory | `ExtractedArchive`, `ArchiveFile`, and `ArchiveDirectory` provide handles for reading, editing, deleting, moving, and saving |
| Reading user-controlled files can accidentally load too much | `readText()` and `readBytes()` have no built-in limit | Bounded reads make limits explicit: `readText(maxBytes = ...)` |
| Updating small metadata files is awkward to do safely | Direct writes can leave half-written files after failures | `writeTextAtomic(...)` writes through a temporary file and swaps it into place |
| Repacking or creating ZIP files is verbose | You manually build `ZipOutputStream` and preserve directory entries yourself | `saveAsZip(...)` and `SimpliDirectory.zipTo(...)` create ZIP output from high-level handles |
| Java and Android integrations often still need `File` | Code has to bounce between `Path`, `File`, and custom checks | `SimpliFile.file` and `SimpliDirectory.file` expose Java `File` views without pushing NIO details into app code |

The library is archive-first, not archive-only. The same entry point also exposes regular file and directory helpers for common filesystem workflows.

## Quick Start

```kotlin
import org.simplifiles.SimpliFiles
import org.simplifiles.files.OverwritePolicy

val workspace = SimpliFiles.directory("workspace").create()

workspace.file("notes/today.txt")
    .writeTextAtomic("Ship small, useful APIs.\n")

workspace.file("notes/today.txt")
    .copyTo(workspace.resolveInside("backup/today.txt"), OverwritePolicy.ERROR)

val text = workspace.file("notes/today.txt")
    .readText(maxBytes = 64 * 1024)
```

## File Recipes

### Atomic Writes

Use atomic writes for files that should never be left half-written, such as JSON metadata, config files, indexes, manifests, and cache descriptors.

```kotlin
val file = SimpliFiles.file("config/settings.json")

file.writeTextAtomic(
    """
    {
      "theme": "system",
      "sync": true
    }
    """.trimIndent()
)
```

### Bounded Reads

Use bounded reads when file size is controlled by a user or external input.

```kotlin
val manifest = SimpliFiles.file("manifest.json")
    .readText(maxBytes = 256 * 1024)
```

If the file is larger than the limit, SimpliFiles throws `FileOperationException`.

### Copy and Move Policies

```kotlin
import org.simplifiles.files.OverwritePolicy

val source = SimpliFiles.file("input/report.txt")

source.copyTo("output/report.txt", OverwritePolicy.ERROR)
source.copyTo("output/latest.txt", OverwritePolicy.REPLACE)
source.moveTo("archive/report.txt", OverwritePolicy.SKIP)
```

Policies:

- `ERROR` fails when the target already exists
- `REPLACE` replaces the target
- `SKIP` leaves the target unchanged

## Directory Recipes

### Zip a Directory

```kotlin
val workspace = SimpliFiles.directory("workspace").create()

workspace.file("reports/summary.txt").writeTextAtomic("Processed 42 records.\n")
workspace.file("reports/details.txt").writeText("Everything completed successfully.\n")

val archive = workspace.zipTo("workspace.zip")
```

With save options:

```kotlin
import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.files.OverwritePolicy

val options = ArchiveSaveOptions.builder()
    .overwritePolicy(OverwritePolicy.REPLACE)
    .compressionLevel(ArchiveSaveOptions.BEST_SPEED_LEVEL)
    .entryFilter { path -> !path.startsWith("tmp/") && !path.endsWith(".log") }
    .build()

workspace.zipTo("workspace.zip", options)
```

### Safe Child Paths

`resolveInside` rejects absolute paths and parent traversal before returning a normalized path inside the directory root.

```kotlin
val root = SimpliFiles.directory("data").create()

val safePath = root.resolveInside("users/alice/profile.json")
root.file("users/alice/profile.json").writeTextAtomic("{}")
```

Unsafe paths throw `UnsafePathException`:

```kotlin
root.file("../outside.txt")
root.file("/etc/passwd")
root.file("C:\\Windows\\system.ini")
```

### Walk Files

```kotlin
val files = SimpliFiles.directory("data")
    .walkFiles()
    .filter { it.extension == "json" }
```

### Copy or Move Directory Trees

```kotlin
val source = SimpliFiles.directory("public").create()

source.copyTo("dist/public", OverwritePolicy.REPLACE)
source.moveTo("archive/public", OverwritePolicy.ERROR)
```

## Archive Recipes

### Validate Before Extracting

```kotlin
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.security.SecurityPolicy

val report = SimpliFiles.archive("upload.zip")
    .withPolicy(SecurityPolicy.strict())
    .validate()

if (report.isSafe) {
    SimpliFiles.archive("upload.zip")
        .withPolicy(SecurityPolicy.strict())
        .extractTo("output")
}
```

### Extract to a Temporary Workspace

Temporary extractions are deleted when the `use` block exits.

```kotlin
SimpliFiles.archive("bundle.zip")
    .extractToTemp()
    .use { archive ->
        val manifest = archive.file("manifest.json").readText()

        archive.file("processed.txt").writeText(manifest)
        archive.find("**/*.tmp").forEach { it.delete() }
        archive.saveAsZip("bundle-clean.zip")
    }
```

### Preview an Extraction Plan

```kotlin
val plan = SimpliFiles.archive("backup.zip")
    .planExtractionTo("restore")

println("entries: ${plan.totalEntries}")
println("bytes: ${plan.totalBytesToWrite}")
println("safe: ${plan.isSafe}")
```

### Progress and Cancellation

```kotlin
import org.simplifiles.archive.ArchiveExtractionOptions
import org.simplifiles.archive.CancellationToken

val token = CancellationToken { Thread.currentThread().isInterrupted }

val options = ArchiveExtractionOptions.builder()
    .bufferSize(128 * 1024)
    .cancellationToken(token)
    .progressListener { progress ->
        println("${progress.entriesProcessed}/${progress.totalEntries}")
    }
    .build()

SimpliFiles.archive("large.zip").extractTo("output", options)
```

## Java Example

```java
import org.simplifiles.SimpliFiles;
import org.simplifiles.archive.ExtractedArchive;
import org.simplifiles.archive.security.SecurityPolicy;
import org.simplifiles.files.OverwritePolicy;

String metadata = SimpliFiles.directory("workspace")
        .file("metadata.json")
        .readText(64 * 1024);

SimpliFiles.file("workspace/metadata.json")
        .copyTo("workspace/metadata.backup.json", OverwritePolicy.ERROR);

try (ExtractedArchive archive = SimpliFiles.archive("bundle.zip")
        .withPolicy(SecurityPolicy.strict())
        .extractToTemp()) {
    archive.file("summary.txt").writeText(metadata);
    archive.saveAsZip("bundle-updated.zip");
}
```

## Features

- Regular file read, write, append, copy, move, delete
- Bounded file reads
- Atomic text and byte writes
- Directory create, list, walk, copy, move, recursive delete
- Safe child path resolution inside a directory root
- Copy and move overwrite policies
- ZIP inspection without extraction
- ZIP validation report
- Dry-run extraction plans
- Safe extraction to a new or empty directory
- Temporary extraction with cleanup on close
- Extraction progress callbacks
- Extraction cancellation tokens
- Configurable extraction buffer size
- Save progress callbacks, cancellation tokens, and buffer size
- Save overwrite policy, compression level, and entry filters
- Glob search for extracted files
- Save modified extracted contents back to ZIP

## Security Defaults

`SecurityPolicy.strict()` is the default archive policy.

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

Regular directory handles also reject child paths that escape their root.

## Errors

Core exception types:

- `FileOperationException`
- `UnsafePathException`
- `ArchiveValidationException`
- `UnsafeArchivePathException`
- `ExtractionTargetException`
- `ArchiveWriteException`
- `ArchiveOperationException`
- `ArchiveOperationCanceledException`
- `UnsupportedArchiveFormatException`
- `CorruptedArchiveException`

## Limitations

- Archive module supports ZIP only
- symlink and hardlink handling is not complete yet
- benchmark coverage is basic

## License

Apache License 2.0. See [LICENSE](LICENSE).
