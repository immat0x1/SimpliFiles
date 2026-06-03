package org.simplifiles.archive

import java.nio.file.Path

/**
 * Dry-run preview for extracting an archive into a target directory.
 */
data class ArchiveExtractionPlan(
    val format: ArchiveFormat?,
    val targetRoot: Path,
    val entries: List<ArchivePlannedEntry>,
    val validationReport: ValidationReport,
) {
    val isSafe: Boolean
        get() = validationReport.isSafe

    val totalEntries: Int
        get() = entries.size

    val totalBytesToWrite: Long
        get() = entries
            .asSequence()
            .filter { it.action.writesFile }
            .map { it.uncompressedSize }
            .filter { it > 0 }
            .sum()
}
