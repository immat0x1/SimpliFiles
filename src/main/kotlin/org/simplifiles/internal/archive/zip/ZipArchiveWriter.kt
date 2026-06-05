package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.archive.ArchiveSaveProgress
import org.simplifiles.archive.ArchivePackEntry
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.files.OverwritePolicy
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

internal object ZipArchiveWriter {
    fun write(
        root: Path,
        output: Path,
        options: ArchiveSaveOptions,
    ) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedOutput = output.toAbsolutePath().normalize()

        if (normalizedOutput.startsWith(normalizedRoot)) {
            throw ArchiveWriteException(output, "output path must be outside source directory")
        }

        checkCanceled(options)

        val outputExists = Files.exists(normalizedOutput)

        if (outputExists) {
            when (options.overwritePolicy) {
                OverwritePolicy.ERROR -> throw ArchiveWriteException(output, "output file already exists")
                OverwritePolicy.SKIP -> return
                OverwritePolicy.REPLACE -> {
                    if (Files.isDirectory(normalizedOutput)) {
                        throw ArchiveWriteException(output, "output path is a directory")
                    }
                }
            }
        }

        normalizedOutput.parent?.let { Files.createDirectories(it) }
        val directories = listDirectories(normalizedRoot, options)
        val files = listFiles(normalizedRoot, options)
        val replacingExisting = outputExists && options.overwritePolicy == OverwritePolicy.REPLACE
        val writePath = if (replacingExisting) {
            Files.createTempFile(normalizedOutput.parent, "${normalizedOutput.fileName}.", ".tmp")
        } else {
            normalizedOutput
        }
        val progress = SaveProgress(
            options = options,
            totalEntries = (directories.size + files.size).toLong(),
            totalBytes = totalFileSize(files),
        )

        try {
            progress.emit(currentEntryPath = null)
            ZipOutputStream(
                newOutputStream(writePath, replacingExisting),
            ).use { zip ->
                zip.setLevel(options.compressionLevel)
                writeDirectories(zip, normalizedRoot, directories, progress)
                writeFiles(zip, normalizedRoot, files, progress, options.bufferSize)
            }
            if (writePath != normalizedOutput) {
                Files.move(writePath, normalizedOutput, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Throwable) {
            Files.deleteIfExists(writePath)
            throw exception
        }
    }

    fun writePack(
        entries: List<ArchivePackEntry>,
        output: Path,
        options: ArchiveSaveOptions,
    ) {
        val normalizedOutput = output.toAbsolutePath().normalize()
        ensurePackOutputIsSafe(entries, output, normalizedOutput)
        checkCanceled(options)
        val outputExists = Files.exists(normalizedOutput)

        if (outputExists) {
            when (options.overwritePolicy) {
                OverwritePolicy.ERROR -> throw ArchiveWriteException(output, "output file already exists")
                OverwritePolicy.SKIP -> return
                OverwritePolicy.REPLACE -> {
                    if (Files.isDirectory(normalizedOutput)) {
                        throw ArchiveWriteException(output, "output path is a directory")
                    }
                }
            }
        }

        normalizedOutput.parent?.let { Files.createDirectories(it) }
        val plannedEntries = planPackEntries(entries, output, options)
        val replacingExisting = outputExists && options.overwritePolicy == OverwritePolicy.REPLACE
        val writePath = if (replacingExisting) {
            Files.createTempFile(normalizedOutput.parent, "${normalizedOutput.fileName}.", ".tmp")
        } else {
            normalizedOutput
        }
        val progress = SaveProgress(
            options = options,
            totalEntries = plannedEntries.size.toLong(),
            totalBytes = plannedEntries.fold(0L) { total, entry ->
                if (entry.isDirectory) {
                    total
                } else if (Long.MAX_VALUE - total < entry.size) {
                    Long.MAX_VALUE
                } else {
                    total + entry.size
                }
            },
        )

        try {
            progress.emit(currentEntryPath = null)
            ZipOutputStream(
                newOutputStream(writePath, replacingExisting),
            ).use { zip ->
                zip.setLevel(options.compressionLevel)
                for (entry in plannedEntries) {
                    progress.checkCanceled()
                    zip.putNextEntry(ZipEntry(entry.archivePath))
                    if (!entry.isDirectory) {
                        Files.newInputStream(entry.source).use { input ->
                            copy(input, zip, options.bufferSize, progress, entry.archivePath)
                        }
                    }
                    zip.closeEntry()
                    progress.entryCompleted(entry.archivePath)
                }
            }
            if (writePath != normalizedOutput) {
                Files.move(writePath, normalizedOutput, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Throwable) {
            Files.deleteIfExists(writePath)
            throw exception
        }
    }

    private fun ensurePackOutputIsSafe(
        entries: List<ArchivePackEntry>,
        output: Path,
        normalizedOutput: Path,
    ) {
        for (entry in entries) {
            val source = entry.source.toAbsolutePath().normalize()
            when (entry) {
                is ArchivePackEntry.DirectoryEntry -> {
                    if (normalizedOutput.startsWith(source)) {
                        throw ArchiveWriteException(output, "output path must be outside source directory")
                    }
                }

                is ArchivePackEntry.FileEntry -> {
                    if (normalizedOutput == source) {
                        throw ArchiveWriteException(output, "output path must not replace a source file")
                    }
                }
            }
        }
    }

    private fun planPackEntries(
        entries: List<ArchivePackEntry>,
        output: Path,
        options: ArchiveSaveOptions,
    ): List<PlannedPackEntry> {
        val plannedEntries = mutableListOf<PlannedPackEntry>()
        val registry = EntryPathRegistry(output)

        for (entry in entries) {
            when (entry) {
                is ArchivePackEntry.FileEntry -> {
                    val source = entry.source.toAbsolutePath().normalize()
                    if (!Files.isRegularFile(source)) {
                        throw ArchiveWriteException(output, "source file does not exist: ${entry.source}")
                    }
                    addPackFile(
                        plannedEntries = plannedEntries,
                        registry = registry,
                        source = source,
                        archivePath = entry.entryPath,
                        options = options,
                    )
                }

                is ArchivePackEntry.DirectoryEntry -> {
                    val source = entry.source.toAbsolutePath().normalize()
                    if (!Files.isDirectory(source)) {
                        throw ArchiveWriteException(output, "source directory does not exist: ${entry.source}")
                    }
                    addPackDirectory(
                        plannedEntries = plannedEntries,
                        registry = registry,
                        source = source,
                        entryPath = entry.entryPath,
                        options = options,
                    )
                }
            }
        }

        return plannedEntries.sortedBy { it.archivePath }
    }

    private fun addPackDirectory(
        plannedEntries: MutableList<PlannedPackEntry>,
        registry: EntryPathRegistry,
        source: Path,
        entryPath: String,
        options: ArchiveSaveOptions,
    ) {
        Files.walk(source).use { stream ->
            stream.asSequence()
                .sortedBy { source.relativize(it).toString() }
                .forEach { current ->
                    val archivePath = packArchivePath(
                        source = source,
                        current = current,
                        entryPath = entryPath,
                    )
                    if (Files.isDirectory(current)) {
                        addPackDirectoryEntry(plannedEntries, registry, current, archivePath, options)
                    } else if (Files.isRegularFile(current)) {
                        addPackFile(plannedEntries, registry, current, archivePath, options)
                    }
                }
        }
    }

    private fun addPackDirectoryEntry(
        plannedEntries: MutableList<PlannedPackEntry>,
        registry: EntryPathRegistry,
        source: Path,
        archivePath: String,
        options: ArchiveSaveOptions,
    ) {
        val directoryPath = archivePath.trimEnd('/') + "/"
        if (!options.entryFilter.include(directoryPath)) {
            return
        }

        registry.addDirectory(directoryPath)
        plannedEntries += PlannedPackEntry(
            source = source,
            archivePath = directoryPath,
            isDirectory = true,
            size = 0,
        )
    }

    private fun addPackFile(
        plannedEntries: MutableList<PlannedPackEntry>,
        registry: EntryPathRegistry,
        source: Path,
        archivePath: String,
        options: ArchiveSaveOptions,
    ) {
        if (!options.entryFilter.include(archivePath)) {
            return
        }

        registry.addFile(archivePath)
        plannedEntries += PlannedPackEntry(
            source = source,
            archivePath = archivePath,
            isDirectory = false,
            size = Files.size(source),
        )
    }

    private fun packArchivePath(
        source: Path,
        current: Path,
        entryPath: String,
    ): String {
        if (current == source) {
            return entryPath
        }

        val relative = source.relativize(current).toString().replace('\\', '/')
        return "$entryPath/$relative"
    }

    private fun newOutputStream(
        path: Path,
        replacingExisting: Boolean,
    ): OutputStream =
        if (replacingExisting) {
            Files.newOutputStream(
                path,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        } else {
            Files.newOutputStream(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
        }

    private fun writeDirectories(
        zip: ZipOutputStream,
        normalizedRoot: Path,
        directories: List<Path>,
        progress: SaveProgress,
    ) {
        for (directory in directories) {
            progress.checkCanceled()
            val archivePath = normalizedRoot.relativize(directory).toString().replace('\\', '/') + "/"
            zip.putNextEntry(ZipEntry(archivePath))
            zip.closeEntry()
            progress.entryCompleted(archivePath)
        }
    }

    private fun writeFiles(
        zip: ZipOutputStream,
        normalizedRoot: Path,
        files: List<Path>,
        progress: SaveProgress,
        bufferSize: Int,
    ) {
        for (file in files) {
            progress.checkCanceled()
            val archivePath = normalizedRoot.relativize(file).toString().replace('\\', '/')
            zip.putNextEntry(ZipEntry(archivePath))
            Files.newInputStream(file).use { input ->
                copy(input, zip, bufferSize, progress, archivePath)
            }
            zip.closeEntry()
            progress.entryCompleted(archivePath)
        }
    }

    private fun listDirectories(
        normalizedRoot: Path,
        options: ArchiveSaveOptions,
    ): List<Path> =
        Files.walk(normalizedRoot).use { stream ->
            stream.asSequence()
                .filter { it != normalizedRoot && Files.isDirectory(it) }
                .filter { options.entryFilter.include(entryPath(normalizedRoot, it) + "/") }
                .sortedBy { normalizedRoot.relativize(it).toString() }
                .toList()
        }

    private fun listFiles(
        normalizedRoot: Path,
        options: ArchiveSaveOptions,
    ): List<Path> =
        Files.walk(normalizedRoot).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .filter { options.entryFilter.include(entryPath(normalizedRoot, it)) }
                .sortedBy { normalizedRoot.relativize(it).toString() }
                .toList()
        }

    private fun entryPath(
        normalizedRoot: Path,
        path: Path,
    ): String = normalizedRoot.relativize(path).toString().replace('\\', '/')

    private fun copy(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int,
        progress: SaveProgress,
        archivePath: String,
    ) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            progress.checkCanceled()
            val read = input.read(buffer)
            if (read < 0) {
                return
            }
            output.write(buffer, 0, read)
            progress.bytesWritten(read.toLong(), archivePath)
        }
    }

    private fun totalFileSize(files: List<Path>): Long =
        files.fold(0L) { total, file ->
            val size = Files.size(file)
            if (Long.MAX_VALUE - total < size) {
                Long.MAX_VALUE
            } else {
                total + size
            }
        }

    private fun checkCanceled(options: ArchiveSaveOptions) {
        if (options.cancellationToken.isCancellationRequested()) {
            throw ArchiveOperationCanceledException()
        }
    }

    private data class PlannedPackEntry(
        val source: Path,
        val archivePath: String,
        val isDirectory: Boolean,
        val size: Long,
    )

    private class EntryPathRegistry(
        private val output: Path,
    ) {
        private val filePaths = mutableSetOf<String>()
        private val fileParentPaths = mutableSetOf<String>()
        private val directoryPaths = mutableSetOf<String>()

        fun addDirectory(path: String) {
            val normalizedPath = normalizePath(path)
            if (filePaths.contains(normalizedPath) || hasExistingPathAncestor(normalizedPath, filePaths)) {
                throw ArchiveWriteException(output, "archive entry path conflicts with an existing file: $path")
            }
            if (!directoryPaths.add(normalizedPath)) {
                throw ArchiveWriteException(output, "archive entry path is duplicated: $path")
            }
        }

        fun addFile(path: String) {
            val normalizedPath = normalizePath(path)
            val conflicts = filePaths.contains(normalizedPath) ||
                directoryPaths.contains(normalizedPath) ||
                fileParentPaths.contains(normalizedPath) ||
                hasExistingPathAncestor(normalizedPath, filePaths) ||
                hasExistingPathDescendant(normalizedPath, directoryPaths)

            if (conflicts) {
                throw ArchiveWriteException(output, "archive entry path conflicts with another entry: $path")
            }

            filePaths += normalizedPath
            addParentPaths(normalizedPath, fileParentPaths)
        }

        private fun normalizePath(path: String): String = path.trimEnd('/')

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

        private fun hasExistingPathDescendant(
            path: String,
            existingPaths: Set<String>,
        ): Boolean {
            val prefix = "$path/"
            return existingPaths.any { it.startsWith(prefix) }
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
    }

    private class SaveProgress(
        private val options: ArchiveSaveOptions,
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
                ArchiveSaveProgress(
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
