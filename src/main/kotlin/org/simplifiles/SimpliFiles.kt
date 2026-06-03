package org.simplifiles

import org.simplifiles.archive.ArchiveSource
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
}
