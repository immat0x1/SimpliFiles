package org.simplifiles.internal.io

import org.simplifiles.exception.FileOperationException
import org.simplifiles.files.DirectoryTransferOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

internal object FileTreeCopier {
    fun validateDirectory(
        source: Path,
        options: DirectoryTransferOptions,
    ) {
        var fileCount = 0L
        var byteCount = 0L

        Files.walk(source).use { stream ->
            stream.asSequence()
                .filter { included(it, options) }
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    fileCount += 1
                    if (fileCount > options.maxFiles) {
                        throw FileOperationException("Directory exceeds copy limit of ${options.maxFiles} files: $source")
                    }

                    byteCount += Files.size(file)
                    if (byteCount > options.maxBytes) {
                        throw FileOperationException("Directory exceeds copy limit of ${options.maxBytes} bytes: $source")
                    }
                }
        }
    }

    fun copyDirectory(
        source: Path,
        target: Path,
        options: DirectoryTransferOptions = DirectoryTransferOptions.defaults(),
    ) {
        validateDirectory(source, options)

        Files.walk(source).use { stream ->
            stream.asSequence()
                .filter { included(it, options) }
                .sortedBy { it.nameCount }
                .forEach { current ->
                    val relative = source.relativize(current)
                    val destination = target.resolve(relative)

                    if (Files.isDirectory(current)) {
                        if (Files.exists(destination) && !Files.isDirectory(destination)) {
                            Files.deleteIfExists(destination)
                        }
                        Files.createDirectories(destination)
                    } else {
                        Files.createDirectories(destination.parent)
                        if (Files.isDirectory(destination)) {
                            FileTreeCleaner.deleteRecursively(destination)
                        }
                        Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }

    private fun included(
        path: Path,
        options: DirectoryTransferOptions,
    ): Boolean =
        when (SymlinkSupport.decide(path, options.symlinkPolicy)) {
            SymlinkDecision.INCLUDE -> true
            SymlinkDecision.SKIP -> false
            SymlinkDecision.FAIL -> throw FileOperationException("Directory contains a symbolic link: $path")
        }
}
