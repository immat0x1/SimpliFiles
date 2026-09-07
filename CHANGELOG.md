# Changelog

## Unreleased

## 0.1.6

This release supersedes the unpublished `0.1.4` and `0.1.5` development lines. Everything below
lands on top of `0.1.3`.

### Security

- **Breaking:** symbolic links inside a source tree are no longer followed by default. `SimpliDirectory.zipTo(...)`, `SimpliFiles.pack().addDirectory(...)`, `SimpliDirectory.copyTo(...)`, and `SimpliDirectory.moveTo(...)` previously read through a link and stored the target's content as a regular file, which could pull data in from outside the tree. They now skip links.
- Added `SymlinkPolicy` with `SKIP` (new default), `ERROR`, and `FOLLOW`, configurable through `ArchiveSaveOptions.symlinkPolicy` and `DirectoryTransferOptions.symlinkPolicy`. Use `FOLLOW` to restore the previous behavior.
- Documented `SecurityPolicy.maxNestedArchiveDepth`, `allowSymlinks`, and `allowHardlinks` as reserved. Their defaults describe what SimpliFiles already does, but raising them has never had an effect.

### Added

- Added `ArchiveSaveOptions.entryTimestamp` for a fixed entry modification time, so the same input tree always produces the same archive bytes.
- Added `equals`, `hashCode`, and `toString` to `SimpliFile`, `SimpliDirectory`, `ArchiveFile`, `ArchiveDirectory`, and `ArchiveSource`. Handles for the same path now compare equal and work as map and set keys.
- Added `toString` to `ArchiveSaveOptions`, `ArchiveExtractionOptions`, and `DirectoryTransferOptions`.
- Added archive save overwrite policy, compression level, and entry filter options.
- Added composable `ArchiveEntryFilter` helpers.
- Added directory transfer options with `DirectoryOverwritePolicy.MERGE`, file-count limits, byte limits, and `SimpliDirectory.clean()`.
- Added `SimpliFiles.pack()` for creating ZIP archives from independent files and directories.
- Added `SimpliFile.writeFrom(...)`, `writeFromAtomic(...)`, `touch()`, `readLines(maxBytes = ...)`, and `forEachLine(maxBytes = ...)`.
- Added `ExtractionTargetPolicy` and `ArchiveSource.extractToDirectory(...)`.
- Added direct `zipTo(..., OverwritePolicy)` shortcuts.

### Fixed

- ZIP entries now carry their source file's modification time. They previously recorded the time of writing, so archives lost source timestamps and were never byte-reproducible.
- Archive format detection no longer misreports a valid ZIP as unsupported when the underlying stream returns fewer bytes than requested on the first read.
- `ArchiveSource.inspect()` now throws `ArchiveOperationException` for a missing path or a directory instead of a raw `NoSuchFileException`.
- `ArchiveSource.validate()` now returns an `archive.unreadable` blocker for a missing path, a directory, or an unreadable file, matching its documented contract of reporting rather than throwing.
- `ArchiveInspection.totalKnownUncompressedSize` and `ArchiveExtractionPlan.totalBytesToWrite` now saturate at `Long.MAX_VALUE` instead of overflowing to a negative value on a crafted archive.
- A truncated ZIP that fails with a plain `IOException` rather than a `ZipException` is now reported as `CorruptedArchiveException`.

### Changed

- **Breaking:** renamed extracted archive repacking from `saveAsZip(...)` to `zipTo(...)` and made it return `SimpliFile`.
- **Breaking:** `ArchiveSaveOptions` and `DirectoryTransferOptions` gained constructor parameters. Kotlin callers using named or default arguments are source compatible but must recompile; the Java `@JvmOverloads` constructors and builders are unchanged.
- Improved `REPLACE` archive saves so existing output is preserved until the new archive is written successfully.
- Improved archive path inspection and validation performance by reducing per-entry allocations.

## 0.1.3

- Added Gradle/Kotlin JVM project setup.
- Added ZIP format detection, inspection, and validation.
- Added strict archive security policy defaults.
- Added safe ZIP extraction to empty directories and temporary sessions.
- Added extracted file and directory APIs.
- Added ZIP repacking with empty directory preservation.
- Added Kotlin and Java usage tests.
- Added JMH benchmark suite for ZIP inspect, validate, and extraction workflows.
- Added extraction progress callbacks and cancellation tokens.
- Improved validation performance for archives with many small entries.
- Added configurable buffer size for ZIP extraction.
- Added save progress callbacks, cancellation tokens, and configurable buffer size for ZIP repacking.
- Added dry-run extraction plans.
- Added Android runtime compatibility hardening for public file and archive APIs.
- Added Java `File` views for `SimpliFile` and `SimpliDirectory`.
- Added `SimpliDirectory.zipTo(...)` for creating ZIP files from directories.

## 0.1.2

Published stable release.
