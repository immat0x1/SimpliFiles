package org.simplifiles.internal.files

import org.simplifiles.exception.UnsafePathException
import java.nio.file.Path

internal object SafePathResolver {
    fun resolveInside(
        root: Path,
        path: String,
    ): Path {
        if (path.isBlank()) {
            throw UnsafePathException(path, "path must not be blank")
        }

        val child = Path.of(path)
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
}
