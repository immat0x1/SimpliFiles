package org.simplifiles.archive

import java.nio.file.Path

/**
 * Single entry in an archive extraction plan.
 */
data class ArchivePlannedEntry(
    val sourcePath: String,
    val normalizedPath: String,
    val destinationPath: Path,
    val action: ArchivePlannedAction,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
)
