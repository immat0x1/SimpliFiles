package org.simplifiles.files

import org.simplifiles.exception.FileOperationException
import org.simplifiles.internal.files.SafePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import org.simplifiles.internal.io.FileTreeCopier
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

/**
 * Directory handle for regular filesystem operations.
 */
class SimpliDirectory internal constructor(
    val path: Path,
) {
    /**
     * Java [File] view of this directory handle.
     */
    val file: File
        get() = File(path.toString())

    /**
     * Java-friendly equivalent of the [file] property.
     */
    fun toFile(): File = file

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

    fun resolveInside(path: String): Path = SafePathResolver.resolveInside(this.path, path)

    fun file(path: String): SimpliFile = SimpliFile(resolveInside(path))

    fun directory(path: String): SimpliDirectory = SimpliDirectory(resolveInside(path))

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

    fun copyTo(target: Path): SimpliDirectory = copyTo(target, OverwritePolicy.REPLACE)

    fun copyTo(
        target: Path,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory {
        if (target.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) {
            throw FileOperationException("Directory cannot be copied into itself: $target")
        }

        if (Files.exists(target)) {
            when (overwritePolicy) {
                OverwritePolicy.ERROR -> throw FileOperationException("Target already exists: $target")
                OverwritePolicy.SKIP -> return SimpliDirectory(target)
                OverwritePolicy.REPLACE -> FileTreeCleaner.deleteRecursively(target)
            }
        }

        FileTreeCopier.copyDirectory(path, target)
        return SimpliDirectory(target)
    }

    fun copyTo(target: String): SimpliDirectory = copyTo(Paths.get(target))

    fun copyTo(
        target: String,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = copyTo(Paths.get(target), overwritePolicy)

    fun copyTo(target: File): SimpliDirectory = copyTo(Paths.get(target.path))

    fun copyTo(
        target: File,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = copyTo(Paths.get(target.path), overwritePolicy)

    fun moveTo(target: Path): SimpliDirectory = moveTo(target, OverwritePolicy.REPLACE)

    fun moveTo(
        target: Path,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory {
        if (target.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) {
            throw FileOperationException("Directory cannot be moved into itself: $target")
        }

        if (Files.exists(target)) {
            when (overwritePolicy) {
                OverwritePolicy.ERROR -> throw FileOperationException("Target already exists: $target")
                OverwritePolicy.SKIP -> return SimpliDirectory(target)
                OverwritePolicy.REPLACE -> FileTreeCleaner.deleteRecursively(target)
            }
        }

        target.parent?.let(Files::createDirectories)
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
        return SimpliDirectory(target)
    }

    fun moveTo(target: String): SimpliDirectory = moveTo(Paths.get(target))

    fun moveTo(
        target: String,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = moveTo(Paths.get(target), overwritePolicy)

    fun moveTo(target: File): SimpliDirectory = moveTo(Paths.get(target.path))

    fun moveTo(
        target: File,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = moveTo(Paths.get(target.path), overwritePolicy)
}
