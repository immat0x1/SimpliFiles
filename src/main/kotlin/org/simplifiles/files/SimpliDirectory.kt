package org.simplifiles.files

import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.exception.FileOperationException
import org.simplifiles.internal.archive.zip.ZipArchiveWriter
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

    fun clean(): SimpliDirectory {
        if (!exists) {
            create()
            return this
        }

        FileTreeCleaner.deleteContents(path)
        return this
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

    fun zipTo(target: Path): SimpliFile = zipTo(target, ArchiveSaveOptions.defaults())

    fun zipTo(
        target: Path,
        options: ArchiveSaveOptions,
    ): SimpliFile {
        if (!exists) {
            throw FileOperationException("Directory does not exist: $path")
        }

        ZipArchiveWriter.write(root = path, output = target, options = options)
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

    fun copyTo(target: Path): SimpliDirectory = copyTo(target, OverwritePolicy.REPLACE)

    fun copyTo(
        target: Path,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = copyTo(target, overwritePolicy.toDirectoryTransferOptions())

    fun copyTo(
        target: Path,
        options: DirectoryTransferOptions,
    ): SimpliDirectory {
        ensureExists()
        rejectSelfTarget(target, "copied")

        validateBeforeReplacing(target, options)
        whenExistingTarget(target, options) {
            return SimpliDirectory(target)
        }

        FileTreeCopier.copyDirectory(path, target, options)
        return SimpliDirectory(target)
    }

    fun copyTo(target: String): SimpliDirectory = copyTo(Paths.get(target))

    fun copyTo(
        target: String,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = copyTo(Paths.get(target), overwritePolicy)

    fun copyTo(
        target: String,
        options: DirectoryTransferOptions,
    ): SimpliDirectory = copyTo(Paths.get(target), options)

    fun copyTo(target: File): SimpliDirectory = copyTo(Paths.get(target.path))

    fun copyTo(
        target: File,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = copyTo(Paths.get(target.path), overwritePolicy)

    fun copyTo(
        target: File,
        options: DirectoryTransferOptions,
    ): SimpliDirectory = copyTo(Paths.get(target.path), options)

    fun moveTo(target: Path): SimpliDirectory = moveTo(target, OverwritePolicy.REPLACE)

    fun moveTo(
        target: Path,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = moveTo(target, overwritePolicy.toDirectoryTransferOptions())

    fun moveTo(
        target: Path,
        options: DirectoryTransferOptions,
    ): SimpliDirectory {
        ensureExists()
        rejectSelfTarget(target, "moved")

        validateBeforeReplacing(target, options)
        whenExistingTarget(target, options) {
            return SimpliDirectory(target)
        }

        if (options.overwritePolicy == DirectoryOverwritePolicy.MERGE && Files.exists(target)) {
            FileTreeCopier.copyDirectory(path, target, options)
            FileTreeCleaner.deleteRecursively(path)
            return SimpliDirectory(target)
        }

        FileTreeCopier.validateDirectory(path, options)
        target.parent?.let(Files::createDirectories)
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
        return SimpliDirectory(target)
    }

    fun moveTo(target: String): SimpliDirectory = moveTo(Paths.get(target))

    fun moveTo(
        target: String,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = moveTo(Paths.get(target), overwritePolicy)

    fun moveTo(
        target: String,
        options: DirectoryTransferOptions,
    ): SimpliDirectory = moveTo(Paths.get(target), options)

    fun moveTo(target: File): SimpliDirectory = moveTo(Paths.get(target.path))

    fun moveTo(
        target: File,
        overwritePolicy: OverwritePolicy,
    ): SimpliDirectory = moveTo(Paths.get(target.path), overwritePolicy)

    fun moveTo(
        target: File,
        options: DirectoryTransferOptions,
    ): SimpliDirectory = moveTo(Paths.get(target.path), options)

    private fun ensureExists() {
        if (!exists) {
            throw FileOperationException("Directory does not exist: $path")
        }
    }

    private fun rejectSelfTarget(
        target: Path,
        operation: String,
    ) {
        if (target.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) {
            throw FileOperationException("Directory cannot be $operation into itself: $target")
        }
    }

    private inline fun whenExistingTarget(
        target: Path,
        options: DirectoryTransferOptions,
        onSkip: () -> Unit,
    ) {
        if (!Files.exists(target)) {
            return
        }

        when (options.overwritePolicy) {
            DirectoryOverwritePolicy.ERROR -> throw FileOperationException("Target already exists: $target")
            DirectoryOverwritePolicy.SKIP -> onSkip()
            DirectoryOverwritePolicy.REPLACE -> FileTreeCleaner.deleteRecursively(target)
            DirectoryOverwritePolicy.MERGE -> Unit
        }
    }

    private fun validateBeforeReplacing(
        target: Path,
        options: DirectoryTransferOptions,
    ) {
        if (Files.exists(target) && options.overwritePolicy == DirectoryOverwritePolicy.REPLACE) {
            FileTreeCopier.validateDirectory(path, options)
        }
    }

    private fun OverwritePolicy.toDirectoryTransferOptions(): DirectoryTransferOptions {
        val directoryPolicy = when (this) {
            OverwritePolicy.ERROR -> DirectoryOverwritePolicy.ERROR
            OverwritePolicy.REPLACE -> DirectoryOverwritePolicy.REPLACE
            OverwritePolicy.SKIP -> DirectoryOverwritePolicy.SKIP
        }
        return DirectoryTransferOptions(overwritePolicy = directoryPolicy)
    }
}
