package org.simplifiles.internal.files

import org.simplifiles.exception.UnsafePathException
import java.nio.file.Path
import java.nio.file.Paths

internal object SafePathResolver {
    fun resolveInside(
        root: Path,
        path: String,
    ): Path {
        if (path.isBlank()) {
            throw UnsafePathException(path, "path must not be blank")
        }

        if (path.startsWith("/") || path.startsWith("\\") || WINDOWS_ABSOLUTE_PATH.matches(path)) {
            throw UnsafePathException(path, "path must be relative")
        }

        if (path.split('/', '\\').any { it == ".." }) {
            throw UnsafePathException(path, "path must not contain parent traversal")
        }

        val child = Paths.get(path)
        if (child.isAbsolute) {
            throw UnsafePathException(path, "path must be relative")
        }

        val normalizedRoot = root.toAbsolutePath().normalize()
        val resolved = normalizedRoot.resolve(child).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw UnsafePathException(path, "path escapes root")
        }

        return resolved
    }

    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[/\\\\].*")
}
