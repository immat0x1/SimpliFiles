package org.simplifiles.archive

import org.simplifiles.internal.saturatingPlus

/**
 * Metadata snapshot for an archive read without extraction.
 */
data class ArchiveInspection(
    val format: ArchiveFormat,
    val entries: List<ArchiveEntryInfo>,
) {
    val entryCount: Int
        get() = entries.size

    /**
     * Sum of known uncompressed entry sizes, saturating at [Long.MAX_VALUE] rather than wrapping.
     */
    val totalKnownUncompressedSize: Long
        get() = entries
            .asSequence()
            .filterNot { it.isDirectory }
            .map { it.uncompressedSize }
            .filter { it >= 0 }
            .fold(0L, ::saturatingPlus)
}
