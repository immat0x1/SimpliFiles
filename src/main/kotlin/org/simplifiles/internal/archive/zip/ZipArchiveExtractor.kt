package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveFormat
import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveExtractionOptions
import org.simplifiles.archive.ArchiveIssue
import org.simplifiles.archive.ArchiveIssueSeverity
import org.simplifiles.archive.ArchiveProgress
import org.simplifiles.archive.ExtractionTargetPolicy
import org.simplifiles.archive.ExtractedArchive
import org.simplifiles.archive.ValidationReport
import org.simplifiles.archive.security.DuplicatePolicy
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.ExtractionTargetException
import org.simplifiles.internal.archive.ArchivePathAnalyzer
import org.simplifiles.internal.archive.ArchivePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import org.simplifiles.internal.saturatingPlus
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.streams.asSequence

internal object ZipArchiveExtractor {
    fun extract(
        source: Path,
        targetRoot: Path,
        policy: SecurityPolicy,
        cleanupOnClose: Boolean,
        entries: List<ArchiveEntryInfo>,
        options: ArchiveExtractionOptions,
    ): ExtractedArchive {
        val root = targetRoot.toAbsolutePath().normalize()
        val targetExisted = Files.exists(root)
        val progress = ExtractionProgress(
            options = options,
            totalEntries = entries.size.toLong(),
            totalBytes = knownUncompressedSize(entries),
        )

        progress.checkCanceled()
        prepareTarget(root, options.targetPolicy)

        return try {
            progress.emit(currentEntryPath = null)
            extractEntries(source, root, policy, progress, options.bufferSize)
            ExtractedArchive(root = root, cleanupOnClose = cleanupOnClose)
        } catch (exception: Throwable) {
            if (targetExisted) {
                FileTreeCleaner.deleteContents(root)
            } else {
                FileTreeCleaner.deleteRecursively(root)
            }
            throw exception
        }
    }

    private fun prepareTarget(
        root: Path,
        targetPolicy: ExtractionTargetPolicy,
    ) {
        if (Files.exists(root)) {
            when (targetPolicy) {
                ExtractionTargetPolicy.ERROR_IF_NOT_EMPTY -> {
                    if (!Files.isDirectory(root)) {
                        throw ExtractionTargetException(root, "target exists but is not a directory")
                    }
                    if (!isDirectoryEmpty(root)) {
                        throw ExtractionTargetException(root, "target directory must be empty")
                    }
                    return
                }

                ExtractionTargetPolicy.CLEAN -> {
                    if (!Files.isDirectory(root)) {
                        throw ExtractionTargetException(root, "target exists but is not a directory")
                    }
                    FileTreeCleaner.deleteContents(root)
                    return
                }

                ExtractionTargetPolicy.REPLACE -> {
                    FileTreeCleaner.deleteRecursively(root)
                }
            }
        }

        Files.createDirectories(root)
    }

    private fun isDirectoryEmpty(root: Path): Boolean =
        Files.list(root).use { stream -> !stream.findAny().isPresent }

    private fun extractEntries(
        source: Path,
        root: Path,
        policy: SecurityPolicy,
        progress: ExtractionProgress,
        bufferSize: Int,
    ) {
        ZipFile(source.toFile()).use { zipFile ->
            val destinationPaths = mutableSetOf<String>()
            var totalWritten = 0L

            for (entry in zipFile.entries().asSequence()) {
                progress.checkCanceled()
                val destinationPath = destinationPathFor(
                    entry = entry,
                    destinationPaths = destinationPaths,
                    policy = policy,
                ) ?: run {
                    progress.entryCompleted(entry.name)
                    continue
                }

                val target = ArchivePathResolver.resolve(
                    root = root,
                    path = destinationPath,
                    allowAbsolute = policy.allowAbsolutePaths,
                )

                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    progress.entryCompleted(destinationPath)
                    continue
                }

                Files.createDirectories(target.parent)

                if (policy.duplicatePolicy == DuplicatePolicy.KEEP_LAST) {
                    Files.deleteIfExists(target)
                }

                val written = zipFile.getInputStream(entry).use { input ->
                    Files.newOutputStream(
                        target,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                    ).use { output ->
                        copyWithLimits(
                            input = input,
                            output = output,
                            entry = entry,
                            totalWritten = totalWritten,
                            policy = policy,
                            progress = progress,
                            destinationPath = destinationPath,
                            bufferSize = bufferSize,
                        )
                    }
                }

                totalWritten = checkedAddTotal(totalWritten, written, policy, entry.name)
                validateRuntimeCompressionRatio(entry, written, policy)
                progress.entryCompleted(destinationPath)
            }
        }
    }

    private fun destinationPathFor(
        entry: ZipEntry,
        destinationPaths: MutableSet<String>,
        policy: SecurityPolicy,
    ): String? {
        val normalizedPath = ArchivePathAnalyzer.analyze(entry.name).normalizedPath
            ?: failValidation(
                code = "archive.entry.path.invalid",
                message = "Archive entry path is invalid.",
                path = entry.name,
            )

        if (destinationPaths.add(normalizedPath)) {
            return normalizedPath
        }

        return when (policy.duplicatePolicy) {
            DuplicatePolicy.ERROR -> failValidation(
                code = "archive.entry.duplicate",
                message = "Archive contains duplicate entry path: $normalizedPath.",
                path = entry.name,
            )

            DuplicatePolicy.KEEP_FIRST -> null
            DuplicatePolicy.KEEP_LAST -> normalizedPath
            DuplicatePolicy.RENAME -> renamedPath(normalizedPath, destinationPaths)
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

    private fun copyWithLimits(
        input: InputStream,
        output: OutputStream,
        entry: ZipEntry,
        totalWritten: Long,
        policy: SecurityPolicy,
        progress: ExtractionProgress,
        destinationPath: String,
        bufferSize: Int,
    ): Long {
        val buffer = ByteArray(bufferSize)
        var entryWritten = 0L

        while (true) {
            progress.checkCanceled()
            val read = input.read(buffer)
            if (read < 0) {
                return entryWritten
            }

            entryWritten = checkedAddEntry(entryWritten, read.toLong(), policy, entry.name)
            checkedAddTotal(totalWritten, entryWritten, policy, entry.name)
            output.write(buffer, 0, read)
            progress.bytesWritten(read.toLong(), destinationPath)
        }
    }

    private fun knownUncompressedSize(entries: List<ArchiveEntryInfo>): Long =
        entries
            .asSequence()
            .filterNot { it.isDirectory }
            .map { it.uncompressedSize }
            .filter { it > 0 }
            .fold(0L, ::saturatingPlus)

    private fun checkedAddEntry(
        current: Long,
        added: Long,
        policy: SecurityPolicy,
        path: String,
    ): Long {
        if (Long.MAX_VALUE - current < added) {
            failValidation(
                code = "archive.entry.size.overflow",
                message = "Archive entry size overflows Long.",
                path = path,
            )
        }

        val next = current + added
        if (next > policy.maxSingleFileSize) {
            failValidation(
                code = "archive.entry.size.too_large",
                message = "Archive entry exceeded ${policy.maxSingleFileSize} bytes while extracting.",
                path = path,
            )
        }

        return next
    }

    private fun checkedAddTotal(
        current: Long,
        added: Long,
        policy: SecurityPolicy,
        path: String,
    ): Long {
        if (Long.MAX_VALUE - current < added) {
            failValidation(
                code = "archive.total_size.overflow",
                message = "Archive total size overflows Long.",
                path = path,
            )
        }

        val next = current + added
        if (next > policy.maxTotalUncompressedSize) {
            failValidation(
                code = "archive.total_size.too_large",
                message = "Archive exceeded ${policy.maxTotalUncompressedSize} total bytes while extracting.",
                path = path,
            )
        }

        return next
    }

    private fun validateRuntimeCompressionRatio(
        entry: ZipEntry,
        written: Long,
        policy: SecurityPolicy,
    ) {
        if (entry.compressedSize < 0 || written <= 0) {
            return
        }

        val ratio = if (entry.compressedSize == 0L) {
            Double.POSITIVE_INFINITY
        } else {
            written.toDouble() / entry.compressedSize.toDouble()
        }

        if (ratio > policy.maxCompressionRatio) {
            failValidation(
                code = "archive.entry.compression_ratio.too_high",
                message = "Archive entry compression ratio is $ratio, limit is ${policy.maxCompressionRatio}.",
                path = entry.name,
            )
        }
    }

    private fun failValidation(
        code: String,
        message: String,
        path: String? = null,
    ): Nothing {
        throw ArchiveValidationException(
            ValidationReport(
                format = ArchiveFormat.ZIP,
                issues = listOf(
                    ArchiveIssue(
                        severity = ArchiveIssueSeverity.ERROR,
                        code = code,
                        message = message,
                        path = path,
                    ),
                ),
            ),
        )
    }

    private class ExtractionProgress(
        private val options: ArchiveExtractionOptions,
        private val totalEntries: Long,
        private val totalBytes: Long,
    ) {
        private var entriesProcessed: Long = 0
        private var bytesWritten: Long = 0

        fun checkCanceled() {
            if (options.cancellationToken.isCancellationRequested()) {
                throw ArchiveOperationCanceledException()
            }
        }

        fun bytesWritten(bytes: Long, currentEntryPath: String) {
            bytesWritten += bytes
            emit(currentEntryPath)
        }

        fun entryCompleted(currentEntryPath: String) {
            entriesProcessed += 1
            emit(currentEntryPath)
        }

        fun emit(currentEntryPath: String?) {
            options.progressListener?.onProgress(
                ArchiveProgress(
                    currentEntryPath = currentEntryPath,
                    entriesProcessed = entriesProcessed,
                    totalEntries = totalEntries,
                    bytesWritten = bytesWritten,
                    totalBytes = totalBytes,
                ),
            )
        }
    }
}
