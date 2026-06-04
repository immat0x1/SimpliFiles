# Changelog

## Unreleased

- Started `0.1.4-SNAPSHOT` development.
- Added archive save overwrite policy, compression level, and entry filter options.
- Added composable `ArchiveEntryFilter` helpers.
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
