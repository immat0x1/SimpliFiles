package org.simplifiles.archive

/**
 * Metadata snapshot for an archive read without extraction.
 */
data class ArchiveInspection(
    val format: ArchiveFormat,
    val entries: List<ArchiveEntryInfo>,
) {
    val entryCount: Int
        get() = entries.size

    val totalKnownUncompressedSize: Long
        get() = entries
            .asSequence()
            .filterNot { it.isDirectory }
            .map { it.uncompressedSize }
            .filter { it >= 0 }
            .sum()
}
