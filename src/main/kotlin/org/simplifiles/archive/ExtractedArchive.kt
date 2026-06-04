package org.simplifiles.archive

import org.simplifiles.internal.archive.ArchivePathResolver
import org.simplifiles.internal.archive.zip.ZipArchiveWriter
import org.simplifiles.internal.io.FileTreeCleaner
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * Working session for files extracted from an archive.
 *
 * Instances returned by `extractToTemp()` delete their temporary root on [close].
 */
class ExtractedArchive internal constructor(
    val root: Path,
    private val cleanupOnClose: Boolean,
) : AutoCloseable {
    /**
     * Lists all regular files currently present in this extracted archive.
     */
    val files: List<ArchiveFile>
        get() = Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .map { file ->
                    ArchiveFile(
                        root = root,
                        path = root.relativize(file).toString().replace('\\', '/'),
                        absolutePath = file,
                    )
                }
                .toList()
        }

    /**
     * Lists all directories currently present in this extracted archive, excluding [root].
     */
    val directories: List<ArchiveDirectory>
        get() = Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { it != root && Files.isDirectory(it) }
                .map { directory ->
                    ArchiveDirectory(
                        root = root,
                        path = root.relativize(directory).toString().replace('\\', '/'),
                        absolutePath = directory,
                    )
                }
                .toList()
        }

    /**
     * Returns a file handle for a path inside this extracted archive root.
     *
     * The file does not need to exist yet.
     */
    fun file(path: String): ArchiveFile {
        val resolved = ArchivePathResolver.resolve(root, path)

        return ArchiveFile(
            root = root,
            path = root.relativize(resolved).toString().replace('\\', '/'),
            absolutePath = resolved,
        )
    }

    /**
     * Returns a directory handle for a path inside this extracted archive root.
     *
     * The directory does not need to exist yet.
     */
    fun directory(path: String): ArchiveDirectory {
        val resolved = ArchivePathResolver.resolve(root, path)

        return ArchiveDirectory(
            root = root,
            path = root.relativize(resolved).toString().replace('\\', '/'),
            absolutePath = resolved,
        )
    }

    /**
     * Finds files by a Java NIO glob pattern.
     */
    fun find(glob: String): List<ArchiveFile> {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")

        return files.filter { file ->
            matcher.matches(Paths.get(file.path))
        }
    }

    /**
     * Saves the current extracted archive contents as a ZIP file.
     *
     * The output file must not already exist and must be outside [root].
     */
    fun saveAsZip(path: Path) {
        saveAsZip(path, ArchiveSaveOptions.defaults())
    }

    /**
     * Saves the current extracted archive contents as a ZIP file.
     *
     * The output file must not already exist and must be outside [root].
     */
    fun saveAsZip(path: Path, options: ArchiveSaveOptions) {
        ZipArchiveWriter.write(root = root, output = path, options = options)
    }

    fun saveAsZip(path: String) {
        saveAsZip(Paths.get(path))
    }

    fun saveAsZip(path: String, options: ArchiveSaveOptions) {
        saveAsZip(Paths.get(path), options)
    }

    fun saveAsZip(file: File) {
        saveAsZip(file.toPath())
    }

    fun saveAsZip(file: File, options: ArchiveSaveOptions) {
        saveAsZip(file.toPath(), options)
    }

    /**
     * Closes this session and deletes the extracted root when it is temporary.
     */
    override fun close() {
        if (cleanupOnClose) {
            FileTreeCleaner.deleteRecursively(root)
        }
    }
}
