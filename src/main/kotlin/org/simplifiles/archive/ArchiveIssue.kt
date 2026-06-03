package org.simplifiles.archive

/**
 * Single issue found during archive validation.
 */
data class ArchiveIssue(
    val severity: ArchiveIssueSeverity,
    val code: String,
    val message: String,
    val path: String? = null,
)
