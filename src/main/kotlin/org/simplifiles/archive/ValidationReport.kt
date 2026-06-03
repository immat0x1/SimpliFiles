package org.simplifiles.archive

/**
 * Result of archive validation.
 */
data class ValidationReport(
    val format: ArchiveFormat? = null,
    val entries: List<ArchiveEntryInfo> = emptyList(),
    val issues: List<ArchiveIssue> = emptyList(),
) {
    val isSafe: Boolean
        get() = issues.none { issue ->
            issue.severity == ArchiveIssueSeverity.ERROR ||
                issue.severity == ArchiveIssueSeverity.BLOCKER
        }
}
