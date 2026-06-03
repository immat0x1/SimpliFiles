package org.simplifiles.archive

import org.simplifiles.exception.ArchiveOperationException
import org.simplifiles.internal.archive.ArchivePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

/**
 * Directory handle inside an extracted archive session.
 *
 * The directory path is always relative to the archive root and uses `/` separators.
 */
class ArchiveDirectory internal constructor(
    private val root: Path,
    val path: String,
    val absolutePath: Path,
) {
    val exists: Boolean
        get() = Files.isDirectory(absolutePath)

    /**
     * Java-friendly equivalent of the [exists] property.
     */
    fun exists(): Boolean = exists

    /**
     * Immediate regular files inside this directory.
     */
    val files: List<ArchiveFile>
        get() {
            if (!exists) {
                return emptyList()
            }

            return Files.list(absolutePath).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) }
                    .map { file -> file.toArchiveFile() }
                    .toList()
            }
        }

    /**
     * Immediate child directories inside this directory.
     */
    val directories: List<ArchiveDirectory>
        get() {
            if (!exists) {
                return emptyList()
            }

            return Files.list(absolutePath).use { stream ->
                stream.asSequence()
                    .filter { Files.isDirectory(it) }
                    .map { directory -> directory.toArchiveDirectory() }
                    .toList()
            }
        }

    /**
     * Recursively lists regular files inside this directory.
     */
    fun walkFiles(): List<ArchiveFile> {
        if (!exists) {
            return emptyList()
        }

        return Files.walk(absolutePath).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .map { file -> file.toArchiveFile() }
                .toList()
        }
    }

    /**
     * Creates this directory and any missing parent directories.
     */
    fun create(): ArchiveDirectory {
        Files.createDirectories(absolutePath)
        return this
    }

    /**
     * Recursively deletes this directory if it exists.
     */
    fun deleteRecursively(): Boolean {
        if (!exists) {
            return false
        }

        FileTreeCleaner.deleteRecursively(absolutePath)
        return true
    }

    /**
     * Recursively copies this directory to another path inside the same extracted archive root.
     */
    fun copyTo(path: String): ArchiveDirectory {
        val target = ArchivePathResolver.resolve(root, path)
        if (target.startsWith(absolutePath)) {
            throw ArchiveOperationException("Directory cannot be copied into itself: $path")
        }

        copyDirectory(absolutePath, target)

        return target.toArchiveDirectory()
    }

    /**
     * Moves this directory to another path inside the same extracted archive root.
     */
    fun moveTo(path: String): ArchiveDirectory {
        val target = ArchivePathResolver.resolve(root, path)
        if (target.startsWith(absolutePath)) {
            throw ArchiveOperationException("Directory cannot be moved into itself: $path")
        }

        Files.createDirectories(target.parent)
        Files.move(absolutePath, target, StandardCopyOption.REPLACE_EXISTING)

        return target.toArchiveDirectory()
    }

    private fun copyDirectory(
        source: Path,
        target: Path,
    ) {
        Files.walk(source).use { stream ->
            stream.asSequence()
                .sortedBy { it.nameCount }
                .forEach { current ->
                    val relative = source.relativize(current)
                    val destination = target.resolve(relative)

                    if (Files.isDirectory(current)) {
                        Files.createDirectories(destination)
                    } else {
                        Files.createDirectories(destination.parent)
                        Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }

    private fun Path.toArchiveFile(): ArchiveFile =
        ArchiveFile(
            root = this@ArchiveDirectory.root,
            path = this@ArchiveDirectory.root.relativize(this).toString().replace('\\', '/'),
            absolutePath = this,
        )

    private fun Path.toArchiveDirectory(): ArchiveDirectory =
        ArchiveDirectory(
            root = this@ArchiveDirectory.root,
            path = this@ArchiveDirectory.root.relativize(this).toString().replace('\\', '/'),
            absolutePath = this,
        )
}
