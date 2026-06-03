package org.simplifiles.internal.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

internal object FileTreeCopier {
    fun copyDirectory(
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
}
