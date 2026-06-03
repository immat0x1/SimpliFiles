package org.simplifiles.internal.archive

import org.simplifiles.exception.UnsafeArchivePathException
import java.nio.file.Path

internal object ArchivePathResolver {
    fun resolve(
        root: Path,
        path: String,
        allowAbsolute: Boolean = false,
    ): Path {
        val rootAbsolute = root.toAbsolutePath().normalize()
        val analysis = ArchivePathAnalyzer.analyze(path)
        val normalizedPath = analysis.normalizedPath

        if (analysis.isAbsolute && !allowAbsolute) {
            throw UnsafeArchivePathException(path, "path must be relative")
        }

        if (analysis.containsParentTraversal) {
            throw UnsafeArchivePathException(path, "path must not contain parent traversal")
        }

        if (analysis.isEmpty || normalizedPath == null) {
            throw UnsafeArchivePathException(path, "path must not be empty")
        }

        val resolved = rootAbsolute.resolve(normalizedPath).normalize()
        if (!resolved.startsWith(rootAbsolute)) {
            throw UnsafeArchivePathException(path, "path escapes archive root")
        }

        return resolved
    }
}
