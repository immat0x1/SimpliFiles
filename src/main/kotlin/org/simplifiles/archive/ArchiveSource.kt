package org.simplifiles.archive

import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveOperationException
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.CorruptedArchiveException
import org.simplifiles.exception.UnsupportedArchiveFormatException
import org.simplifiles.files.SimpliDirectory
import org.simplifiles.internal.archive.ArchiveExtractionPlanner
import org.simplifiles.internal.archive.ArchiveFormatDetector
import org.simplifiles.internal.archive.ArchiveValidator
import org.simplifiles.internal.archive.zip.ZipArchiveExtractor
import org.simplifiles.internal.archive.zip.ZipArchiveReader
import org.simplifiles.internal.io.FileTreeCleaner
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configurable archive input.
 *
 * Use this type to inspect, validate, or safely extract an archive.
 * Instances are immutable: changing the security policy returns a new source.
 */
class ArchiveSource internal constructor(
    val path: Path,
    val policy: SecurityPolicy = SecurityPolicy.strict(),
) {
    /**
     * Returns a new source with the given security policy.
     */
    fun withPolicy(policy: SecurityPolicy): ArchiveSource = ArchiveSource(path, policy)

    /**
     * Reads archive metadata without extracting files.
     *
     * @throws ArchiveOperationException when the path is missing or is not a regular file.
     * @throws UnsupportedArchiveFormatException when the format is not supported.
     * @throws CorruptedArchiveException when the archive looks supported but cannot be read.
     */
    fun inspect(): ArchiveInspection {
        ensureReadableFile()

        val format = ArchiveFormatDetector.detect(path)
            ?: throw UnsupportedArchiveFormatException(path)

        return when (format) {
            ArchiveFormat.ZIP -> ZipArchiveReader.inspect(path)
        }
    }

    /**
     * Validates the archive against the current [SecurityPolicy].
     *
     * This method returns a report instead of throwing for unsafe, unsupported,
     * or corrupted archives.
     */
    fun validate(): ValidationReport {
        unreadableFileReason()?.let { reason ->
            return blockerReport(code = "archive.unreadable", message = reason)
        }

        val format = try {
            ArchiveFormatDetector.detect(path)
        } catch (exception: IOException) {
            return blockerReport(
                code = "archive.unreadable",
                message = exception.message ?: "Archive cannot be read.",
            )
        } ?: return blockerReport(
            code = "archive.format.unsupported",
            message = "Unsupported archive format.",
        )

        val inspection = try {
            when (format) {
                ArchiveFormat.ZIP -> ZipArchiveReader.inspect(path)
            }
        } catch (exception: CorruptedArchiveException) {
            return ValidationReport(
                format = format,
                issues = listOf(
                    ArchiveIssue(
                        severity = ArchiveIssueSeverity.BLOCKER,
                        code = "archive.corrupted",
                        message = exception.message ?: "Archive is corrupted.",
                    ),
                ),
            )
        }

        return ArchiveValidator.validate(inspection, policy)
    }

    /**
     * Builds a dry-run extraction plan without writing files.
     */
    fun planExtractionTo(path: Path): ArchiveExtractionPlan {
        val report = validate()

        return ArchiveExtractionPlanner.plan(
            targetRoot = path,
            report = report,
            policy = policy,
        )
    }

    fun planExtractionTo(path: String): ArchiveExtractionPlan =
        planExtractionTo(Paths.get(path))

    fun planExtractionTo(file: File): ArchiveExtractionPlan =
        planExtractionTo(Paths.get(file.path))

    /**
     * Safely extracts the archive into a new or empty target directory.
     *
     * @throws ArchiveValidationException when validation fails.
     */
    fun extractTo(path: Path): ExtractedArchive =
        extractTo(path, ArchiveExtractionOptions.defaults())

    /**
     * Safely extracts the archive into a new or empty target directory.
     *
     * @throws ArchiveValidationException when validation fails.
     */
    fun extractTo(path: Path, options: ArchiveExtractionOptions): ExtractedArchive {
        ensureNotCanceled(options)
        val report = validate()
        if (!report.isSafe) {
            throw ArchiveValidationException(report)
        }
        ensureNotCanceled(options)

        return when (report.format) {
            ArchiveFormat.ZIP -> ZipArchiveExtractor.extract(
                source = this.path,
                targetRoot = path,
                policy = policy,
                cleanupOnClose = false,
                entries = report.entries,
                options = options,
            )

            null -> throw ArchiveValidationException(report)
        }
    }

    fun extractTo(path: String): ExtractedArchive = extractTo(Paths.get(path))

    fun extractTo(path: String, options: ArchiveExtractionOptions): ExtractedArchive =
        extractTo(Paths.get(path), options)

    fun extractTo(file: File): ExtractedArchive = extractTo(Paths.get(file.path))

    fun extractTo(file: File, options: ArchiveExtractionOptions): ExtractedArchive =
        extractTo(Paths.get(file.path), options)

    /**
     * Safely extracts the archive and returns a directory handle for the target.
     */
    fun extractToDirectory(path: Path): SimpliDirectory =
        extractToDirectory(path, ArchiveExtractionOptions.defaults())

    /**
     * Safely extracts the archive and returns a directory handle for the target.
     */
    fun extractToDirectory(path: Path, options: ArchiveExtractionOptions): SimpliDirectory =
        extractTo(path, options).use { archive ->
            SimpliDirectory(archive.root)
        }

    fun extractToDirectory(path: String): SimpliDirectory =
        extractToDirectory(Paths.get(path))

    fun extractToDirectory(path: String, options: ArchiveExtractionOptions): SimpliDirectory =
        extractToDirectory(Paths.get(path), options)

    fun extractToDirectory(file: File): SimpliDirectory =
        extractToDirectory(Paths.get(file.path))

    fun extractToDirectory(file: File, options: ArchiveExtractionOptions): SimpliDirectory =
        extractToDirectory(Paths.get(file.path), options)

    /**
     * Safely extracts the archive into a temporary directory.
     *
     * The temporary directory is deleted when the returned [ExtractedArchive] is closed.
     */
    fun extractToTemp(): ExtractedArchive =
        extractToTemp(ArchiveExtractionOptions.defaults())

    /**
     * Safely extracts the archive into a temporary directory.
     *
     * The temporary directory is deleted when the returned [ExtractedArchive] is closed.
     */
    fun extractToTemp(options: ArchiveExtractionOptions): ExtractedArchive {
        ensureNotCanceled(options)
        val tempRoot = Files.createTempDirectory("simplifiles-")

        return try {
            val report = validate()
            if (!report.isSafe) {
                throw ArchiveValidationException(report)
            }
            ensureNotCanceled(options)

            when (report.format) {
                ArchiveFormat.ZIP -> ZipArchiveExtractor.extract(
                    source = path,
                    targetRoot = tempRoot,
                    policy = policy,
                    cleanupOnClose = true,
                    entries = report.entries,
                    options = options,
                )

                null -> throw ArchiveValidationException(report)
            }
        } catch (exception: Throwable) {
            FileTreeCleaner.deleteRecursively(tempRoot)
            throw exception
        }
    }

    private fun ensureNotCanceled(options: ArchiveExtractionOptions) {
        if (options.cancellationToken.isCancellationRequested()) {
            throw org.simplifiles.exception.ArchiveOperationCanceledException()
        }
    }

    /**
     * Returns why this path cannot be read as an archive, or null when it can be.
     */
    private fun unreadableFileReason(): String? = when {
        !Files.exists(path) -> "Archive file does not exist."
        !Files.isRegularFile(path) -> "Archive path is not a regular file."
        else -> null
    }

    private fun ensureReadableFile() {
        unreadableFileReason()?.let { reason ->
            throw ArchiveOperationException("$reason Path: $path")
        }
    }

    private fun blockerReport(
        code: String,
        message: String,
    ): ValidationReport = ValidationReport(
        issues = listOf(
            ArchiveIssue(
                severity = ArchiveIssueSeverity.BLOCKER,
                code = code,
                message = message,
            ),
        ),
    )

    override fun equals(other: Any?): Boolean =
        this === other || (other is ArchiveSource && path == other.path && policy == other.policy)

    override fun hashCode(): Int = 31 * path.hashCode() + policy.hashCode()

    override fun toString(): String = "ArchiveSource(path=$path, policy=$policy)"
}
