package org.simplifiles.archive

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationReportTest {
    @Test
    fun `report is safe when it has no blocking issues`() {
        val report = ValidationReport(
            issues = listOf(
                ArchiveIssue(
                    severity = ArchiveIssueSeverity.WARNING,
                    code = "archive.warning",
                    message = "Warning-only issue.",
                ),
            ),
        )

        assertTrue(report.isSafe)
    }

    @Test
    fun `report is unsafe when it has an error`() {
        val report = ValidationReport(
            issues = listOf(
                ArchiveIssue(
                    severity = ArchiveIssueSeverity.ERROR,
                    code = "archive.error",
                    message = "Unsafe issue.",
                ),
            ),
        )

        assertFalse(report.isSafe)
    }
}
