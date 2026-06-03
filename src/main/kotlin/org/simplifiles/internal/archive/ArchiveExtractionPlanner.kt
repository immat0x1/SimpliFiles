package org.simplifiles.internal.archive

import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveExtractionPlan
import org.simplifiles.archive.ArchivePlannedAction
import org.simplifiles.archive.ArchivePlannedEntry
import org.simplifiles.archive.ValidationReport
import org.simplifiles.archive.security.DuplicatePolicy
import org.simplifiles.archive.security.SecurityPolicy
import java.nio.file.Path

internal object ArchiveExtractionPlanner {
    fun plan(
        targetRoot: Path,
        report: ValidationReport,
        policy: SecurityPolicy,
    ): ArchiveExtractionPlan {
        val normalizedTargetRoot = targetRoot.toAbsolutePath().normalize()
        val entries = if (report.isSafe) {
            planEntries(
                targetRoot = normalizedTargetRoot,
                entries = report.entries,
                policy = policy,
            )
        } else {
            emptyList()
        }

        return ArchiveExtractionPlan(
            format = report.format,
            targetRoot = normalizedTargetRoot,
            entries = entries,
            validationReport = report,
        )
    }

    private fun planEntries(
        targetRoot: Path,
        entries: List<ArchiveEntryInfo>,
        policy: SecurityPolicy,
    ): List<ArchivePlannedEntry> {
        val destinationPaths = mutableSetOf<String>()
        val plannedEntries = ArrayList<ArchivePlannedEntry>(entries.size)

        for (entry in entries) {
            val normalizedPath = entry.normalizedPath ?: continue
            val plannedPath = plannedPathFor(
                entry = entry,
                normalizedPath = normalizedPath,
                destinationPaths = destinationPaths,
                policy = policy,
            )

            val destinationPath = plannedPath.destinationPath?.let { path ->
                targetRoot.resolve(path).normalize()
            } ?: targetRoot.resolve(normalizedPath).normalize()

            plannedEntries += ArchivePlannedEntry(
                sourcePath = entry.path,
                normalizedPath = plannedPath.destinationPath ?: normalizedPath,
                destinationPath = destinationPath,
                action = plannedPath.action,
                isDirectory = entry.isDirectory,
                compressedSize = entry.compressedSize,
                uncompressedSize = entry.uncompressedSize,
            )
        }

        return plannedEntries
    }

    private fun plannedPathFor(
        entry: ArchiveEntryInfo,
        normalizedPath: String,
        destinationPaths: MutableSet<String>,
        policy: SecurityPolicy,
    ): PlannedPath {
        if (destinationPaths.add(normalizedPath)) {
            return PlannedPath(
                destinationPath = normalizedPath,
                action = if (entry.isDirectory) {
                    ArchivePlannedAction.CREATE_DIRECTORY
                } else {
                    ArchivePlannedAction.CREATE_FILE
                },
            )
        }

        return when (policy.duplicatePolicy) {
            DuplicatePolicy.ERROR -> PlannedPath(
                destinationPath = normalizedPath,
                action = if (entry.isDirectory) {
                    ArchivePlannedAction.CREATE_DIRECTORY
                } else {
                    ArchivePlannedAction.CREATE_FILE
                },
            )

            DuplicatePolicy.KEEP_FIRST -> PlannedPath(
                destinationPath = null,
                action = ArchivePlannedAction.SKIP_DUPLICATE,
            )

            DuplicatePolicy.KEEP_LAST -> PlannedPath(
                destinationPath = normalizedPath,
                action = ArchivePlannedAction.REPLACE_DUPLICATE,
            )

            DuplicatePolicy.RENAME -> PlannedPath(
                destinationPath = renamedPath(normalizedPath, destinationPaths),
                action = ArchivePlannedAction.RENAME_DUPLICATE,
            )
        }
    }

    private fun renamedPath(
        path: String,
        destinationPaths: MutableSet<String>,
    ): String {
        val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
        val fileName = path.substringAfterLast('/')
        val base = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")

        for (index in 1..Int.MAX_VALUE) {
            val renamedFileName = if (extension.isEmpty()) {
                "$base-$index"
            } else {
                "$base-$index.$extension"
            }
            val candidate = if (directory.isEmpty()) {
                renamedFileName
            } else {
                "$directory/$renamedFileName"
            }

            if (destinationPaths.add(candidate)) {
                return candidate
            }
        }

        error("Unable to generate unique archive entry name for $path.")
    }

    private data class PlannedPath(
        val destinationPath: String?,
        val action: ArchivePlannedAction,
    )
}
