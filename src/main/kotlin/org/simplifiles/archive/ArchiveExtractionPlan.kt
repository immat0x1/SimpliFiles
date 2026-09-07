package org.simplifiles.archive

import org.simplifiles.internal.saturatingPlus
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

    /**
     * Bytes the plan expects to write, saturating at [Long.MAX_VALUE] rather than wrapping.
     */
    val totalBytesToWrite: Long
        get() = entries
            .asSequence()
            .filter { it.action.writesFile }
            .map { it.uncompressedSize }
            .filter { it > 0 }
            .fold(0L, ::saturatingPlus)
}
