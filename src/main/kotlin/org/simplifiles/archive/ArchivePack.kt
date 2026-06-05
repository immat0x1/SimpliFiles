package org.simplifiles.archive

import org.simplifiles.exception.FileOperationException
import org.simplifiles.exception.UnsafeArchivePathException
import org.simplifiles.files.SimpliFile
import org.simplifiles.internal.archive.ArchivePathAnalyzer
import org.simplifiles.internal.archive.zip.ZipArchiveWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Builds ZIP archives from independent files and directories.
 */
class ArchivePack internal constructor() {
    private val entries = mutableListOf<ArchivePackEntry>()

    fun addFile(source: Path): ArchivePack = addFile(source, defaultEntryPath(source))

    fun addFile(
        source: Path,
        entryPath: String,
    ): ArchivePack = apply {
        if (!Files.isRegularFile(source)) {
            throw FileOperationException("File does not exist: $source")
        }
        entries += ArchivePackEntry.FileEntry(
            source = source,
            entryPath = normalizeEntryPath(entryPath),
        )
    }

    fun addFile(source: String): ArchivePack = addFile(Paths.get(source))

    fun addFile(
        source: String,
        entryPath: String,
    ): ArchivePack = addFile(Paths.get(source), entryPath)

    fun addFile(source: File): ArchivePack = addFile(Paths.get(source.path))

    fun addFile(
        source: File,
        entryPath: String,
    ): ArchivePack = addFile(Paths.get(source.path), entryPath)

    fun addDirectory(source: Path): ArchivePack = addDirectory(source, defaultEntryPath(source))

    fun addDirectory(
        source: Path,
        entryPath: String,
    ): ArchivePack = apply {
        if (!Files.isDirectory(source)) {
            throw FileOperationException("Directory does not exist: $source")
        }
        entries += ArchivePackEntry.DirectoryEntry(
            source = source,
            entryPath = normalizeEntryPath(entryPath),
        )
    }

    fun addDirectory(source: String): ArchivePack = addDirectory(Paths.get(source))

    fun addDirectory(
        source: String,
        entryPath: String,
    ): ArchivePack = addDirectory(Paths.get(source), entryPath)

    fun addDirectory(source: File): ArchivePack = addDirectory(Paths.get(source.path))

    fun addDirectory(
        source: File,
        entryPath: String,
    ): ArchivePack = addDirectory(Paths.get(source.path), entryPath)

    fun zipTo(target: Path): SimpliFile = zipTo(target, ArchiveSaveOptions.defaults())

    fun zipTo(
        target: Path,
        options: ArchiveSaveOptions,
    ): SimpliFile {
        ZipArchiveWriter.writePack(entries, target, options)
        return SimpliFile(target)
    }

    fun zipTo(target: String): SimpliFile = zipTo(Paths.get(target))

    fun zipTo(
        target: String,
        options: ArchiveSaveOptions,
    ): SimpliFile = zipTo(Paths.get(target), options)

    fun zipTo(target: File): SimpliFile = zipTo(Paths.get(target.path))

    fun zipTo(
        target: File,
        options: ArchiveSaveOptions,
    ): SimpliFile = zipTo(Paths.get(target.path), options)

    private fun defaultEntryPath(source: Path): String {
        return source.fileName?.toString()
            ?: throw FileOperationException("Source path must include a file name: $source")
    }

    private fun normalizeEntryPath(path: String): String {
        val analysis = ArchivePathAnalyzer.analyze(path)

        if (analysis.isAbsolute) {
            throw UnsafeArchivePathException(path, "path must be relative")
        }
        if (analysis.containsParentTraversal) {
            throw UnsafeArchivePathException(path, "path must not contain parent traversal")
        }

        return analysis.normalizedPath ?: throw UnsafeArchivePathException(path, "path must not be empty")
    }
}

internal sealed class ArchivePackEntry {
    abstract val source: Path
    abstract val entryPath: String

    data class FileEntry(
        override val source: Path,
        override val entryPath: String,
    ) : ArchivePackEntry()

    data class DirectoryEntry(
        override val source: Path,
        override val entryPath: String,
    ) : ArchivePackEntry()
}
