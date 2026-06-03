package org.simplifiles.internal.archive

import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveInspection
import org.simplifiles.archive.ArchiveIssue
import org.simplifiles.archive.ArchiveIssueSeverity
import org.simplifiles.archive.ValidationReport
import org.simplifiles.archive.security.DuplicatePolicy
import org.simplifiles.archive.security.SecurityPolicy
import java.util.zip.ZipEntry

internal object ArchiveValidator {
    fun validate(
        inspection: ArchiveInspection,
        policy: SecurityPolicy,
    ): ValidationReport {
        val issues = mutableListOf<ArchiveIssue>()

        if (inspection.entries.size > policy.maxEntries) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entries.too_many",
                message = "Archive contains ${inspection.entries.size} entries, limit is ${policy.maxEntries}.",
            )
        }

        validateEntries(inspection.entries, policy, issues)

        return ValidationReport(
            format = inspection.format,
            entries = inspection.entries,
            issues = issues,
        )
    }

    private fun validateEntries(
        entries: List<ArchiveEntryInfo>,
        policy: SecurityPolicy,
        issues: MutableList<ArchiveIssue>,
    ) {
        val seenPaths = mutableSetOf<String>()
        val filePaths = mutableSetOf<String>()
        val fileParentPaths = mutableSetOf<String>()
        val directoryPaths = mutableSetOf<String>()
        var totalUncompressedSize = 0L
        var totalOverflowReported = false

        for (entry in entries) {
            val path = ArchivePathAnalyzer.analyze(entry.path)
            validatePath(entry, path, policy, issues)
            validateCompressionMethod(entry, issues)

            path.normalizedPath?.let { normalizedPath ->
                if (policy.duplicatePolicy == DuplicatePolicy.ERROR && !seenPaths.add(normalizedPath)) {
                    issues += ArchiveIssue(
                        severity = ArchiveIssueSeverity.ERROR,
                        code = "archive.entry.duplicate",
                        message = "Archive contains duplicate entry path: $normalizedPath.",
                        path = entry.path,
                    )
                }
            }

            path.normalizedPath?.let { normalizedPath ->
                validatePathConflict(
                    entry = entry,
                    normalizedPath = normalizedPath,
                    filePaths = filePaths,
                    fileParentPaths = fileParentPaths,
                    directoryPaths = directoryPaths,
                    issues = issues,
                )
                if (entry.isDirectory) {
                    directoryPaths += normalizedPath
                } else {
                    filePaths += normalizedPath
                    addParentPaths(normalizedPath, fileParentPaths)
                }
            }

            if (!entry.isDirectory) {
                validateEntrySize(entry, policy, issues)
                validateCompressionRatio(entry, policy, issues)

                if (entry.uncompressedSize >= 0) {
                    if (Long.MAX_VALUE - totalUncompressedSize < entry.uncompressedSize) {
                        if (!totalOverflowReported) {
                            issues += ArchiveIssue(
                                severity = ArchiveIssueSeverity.ERROR,
                                code = "archive.total_size.overflow",
                                message = "Total uncompressed size overflows Long.",
                            )
                            totalOverflowReported = true
                        }
                    } else {
                        totalUncompressedSize += entry.uncompressedSize
                    }
                }
            }
        }

        if (totalUncompressedSize > policy.maxTotalUncompressedSize) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.total_size.too_large",
                message = "Archive uncompressed size is $totalUncompressedSize bytes, limit is ${policy.maxTotalUncompressedSize}.",
            )
        }
    }

    private fun validatePathConflict(
        entry: ArchiveEntryInfo,
        normalizedPath: String,
        filePaths: Set<String>,
        fileParentPaths: Set<String>,
        directoryPaths: Set<String>,
        issues: MutableList<ArchiveIssue>,
    ) {
        val conflicts = if (entry.isDirectory) {
            filePaths.contains(normalizedPath) || hasExistingPathAncestor(normalizedPath, filePaths)
        } else {
            directoryPaths.contains(normalizedPath) ||
                hasExistingPathAncestor(normalizedPath, filePaths) ||
                fileParentPaths.contains(normalizedPath)
        }

        if (conflicts) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.path.conflict",
                message = "Archive entry path conflicts with another file or directory path: $normalizedPath.",
                path = entry.path,
            )
        }
    }

    private fun hasExistingPathAncestor(
        path: String,
        existingPaths: Set<String>,
    ): Boolean {
        var separatorIndex = path.indexOf('/')
        while (separatorIndex >= 0) {
            if (existingPaths.contains(path.substring(0, separatorIndex))) {
                return true
            }
            separatorIndex = path.indexOf('/', startIndex = separatorIndex + 1)
        }

        return false
    }

    private fun addParentPaths(
        path: String,
        parentPaths: MutableSet<String>,
    ) {
        var separatorIndex = path.indexOf('/')
        while (separatorIndex >= 0) {
            parentPaths += path.substring(0, separatorIndex)
            separatorIndex = path.indexOf('/', startIndex = separatorIndex + 1)
        }
    }

    private fun validatePath(
        entry: ArchiveEntryInfo,
        path: ArchivePathAnalysis,
        policy: SecurityPolicy,
        issues: MutableList<ArchiveIssue>,
    ) {
        if (path.isEmpty) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.path.empty",
                message = "Archive entry path is empty.",
                path = entry.path,
            )
        }

        if (!policy.allowAbsolutePaths && path.isAbsolute) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.path.absolute",
                message = "Archive entry path is absolute.",
                path = entry.path,
            )
        }

        if (path.containsParentTraversal) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.path.traversal",
                message = "Archive entry path contains parent traversal.",
                path = entry.path,
            )
        }
    }

    private fun validateCompressionMethod(
        entry: ArchiveEntryInfo,
        issues: MutableList<ArchiveIssue>,
    ) {
        if (entry.compressionMethod != ZipEntry.STORED && entry.compressionMethod != ZipEntry.DEFLATED) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.method.unsupported",
                message = "Archive entry uses unsupported compression method: ${entry.compressionMethod}.",
                path = entry.path,
            )
        }
    }

    private fun validateEntrySize(
        entry: ArchiveEntryInfo,
        policy: SecurityPolicy,
        issues: MutableList<ArchiveIssue>,
    ) {
        if (entry.uncompressedSize < 0) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.size.unknown",
                message = "Archive entry uncompressed size is unknown.",
                path = entry.path,
            )
            return
        }

        if (entry.uncompressedSize > policy.maxSingleFileSize) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.size.too_large",
                message = "Archive entry is ${entry.uncompressedSize} bytes, limit is ${policy.maxSingleFileSize}.",
                path = entry.path,
            )
        }
    }

    private fun validateCompressionRatio(
        entry: ArchiveEntryInfo,
        policy: SecurityPolicy,
        issues: MutableList<ArchiveIssue>,
    ) {
        if (entry.compressedSize < 0 || entry.uncompressedSize <= 0) {
            return
        }

        val ratio = if (entry.compressedSize == 0L) {
            Double.POSITIVE_INFINITY
        } else {
            entry.uncompressedSize.toDouble() / entry.compressedSize.toDouble()
        }

        if (ratio > policy.maxCompressionRatio) {
            issues += ArchiveIssue(
                severity = ArchiveIssueSeverity.ERROR,
                code = "archive.entry.compression_ratio.too_high",
                message = "Archive entry compression ratio is $ratio, limit is ${policy.maxCompressionRatio}.",
                path = entry.path,
            )
        }
    }
}
