package org.simplifiles.files

import org.simplifiles.exception.FileOperationException
import org.simplifiles.internal.files.SafePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import org.simplifiles.internal.io.FileTreeCopier
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

/**
 * Directory handle for regular filesystem operations.
 */
class SimpliDirectory internal constructor(
    val path: Path,
) {
    val exists: Boolean
        get() = Files.isDirectory(path)

    /**
     * Java-friendly equivalent of the [exists] property.
     */
    fun exists(): Boolean = exists

    val files: List<SimpliFile>
        get() {
            if (!exists) {
                return emptyList()
            }

            return Files.list(path).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) }
                    .map(::SimpliFile)
                    .toList()
            }
        }

    val directories: List<SimpliDirectory>
        get() {
            if (!exists) {
                return emptyList()
            }

            return Files.list(path).use { stream ->
                stream.asSequence()
                    .filter { Files.isDirectory(it) }
                    .map(::SimpliDirectory)
                    .toList()
            }
        }

    fun create(): SimpliDirectory {
        Files.createDirectories(path)
        return this
    }

    fun deleteRecursively(): Boolean {
        if (!exists) {
            return false
        }

        FileTreeCleaner.deleteRecursively(path)
        return true
    }

    fun file(path: String): SimpliFile = SimpliFile(SafePathResolver.resolveInside(this.path, path))

    fun directory(path: String): SimpliDirectory = SimpliDirectory(SafePathResolver.resolveInside(this.path, path))

    fun contains(target: Path): Boolean {
        val root = path.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()
        return normalizedTarget == root || normalizedTarget.startsWith(root)
    }

    fun walkFiles(): List<SimpliFile> {
        if (!exists) {
            return emptyList()
        }

        return Files.walk(path).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .map(::SimpliFile)
                .toList()
        }
    }

    fun copyTo(target: Path): SimpliDirectory {
        if (target.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) {
            throw FileOperationException("Directory cannot be copied into itself: $target")
        }

        FileTreeCopier.copyDirectory(path, target)
        return SimpliDirectory(target)
    }

    fun copyTo(target: String): SimpliDirectory = copyTo(Path.of(target))

    fun copyTo(target: File): SimpliDirectory = copyTo(target.toPath())

    fun moveTo(target: Path): SimpliDirectory {
        if (target.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) {
            throw FileOperationException("Directory cannot be moved into itself: $target")
        }

        target.parent?.let(Files::createDirectories)
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
        return SimpliDirectory(target)
    }

    fun moveTo(target: String): SimpliDirectory = moveTo(Path.of(target))

    fun moveTo(target: File): SimpliDirectory = moveTo(target.toPath())

}
