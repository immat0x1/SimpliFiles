package org.simplifiles

import org.simplifiles.archive.ArchiveSource
import org.simplifiles.files.SimpliDirectory
import org.simplifiles.files.SimpliFile
import java.io.File
import java.nio.file.Path

/**
 * Public entry point for SimpliFiles archive and file operations.
 */
object SimpliFiles {
    /**
     * Creates an archive source from a filesystem path.
     */
    @JvmStatic
    fun archive(path: Path): ArchiveSource = ArchiveSource(path)

    /**
     * Creates an archive source from a path string.
     */
    @JvmStatic
    fun archive(path: String): ArchiveSource = archive(Path.of(path))

    /**
     * Creates an archive source from a Java [File].
     */
    @JvmStatic
    fun archive(file: File): ArchiveSource = archive(file.toPath())

    /**
     * Creates a file handle from a filesystem path.
     */
    @JvmStatic
    fun file(path: Path): SimpliFile = SimpliFile(path)

    /**
     * Creates a file handle from a path string.
     */
    @JvmStatic
    fun file(path: String): SimpliFile = file(Path.of(path))

    /**
     * Creates a file handle from a Java [File].
     */
    @JvmStatic
    fun file(file: File): SimpliFile = file(file.toPath())

    /**
     * Creates a directory handle from a filesystem path.
     */
    @JvmStatic
    fun directory(path: Path): SimpliDirectory = SimpliDirectory(path)

    /**
     * Creates a directory handle from a path string.
     */
    @JvmStatic
    fun directory(path: String): SimpliDirectory = directory(Path.of(path))

    /**
     * Creates a directory handle from a Java [File].
     */
    @JvmStatic
    fun directory(file: File): SimpliDirectory = directory(file.toPath())
}
