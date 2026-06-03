package org.simplifiles.archive

/**
 * Metadata for a single archive entry discovered during inspection.
 */
data class ArchiveEntryInfo(
    val path: String,
    val normalizedPath: String?,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val compressionMethod: Int,
)
