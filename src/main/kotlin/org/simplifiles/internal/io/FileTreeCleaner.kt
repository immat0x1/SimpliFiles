package org.simplifiles.internal.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.streams.asSequence

internal object FileTreeCleaner {
    fun deleteContents(root: Path) {
        if (!root.exists()) {
            return
        }

        Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { it != root }
                .sortedByDescending { it.nameCount }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    fun deleteRecursively(root: Path) {
        if (!root.exists()) {
            return
        }

        Files.walk(root).use { stream ->
            stream.asSequence()
                .sortedByDescending { it.nameCount }
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
