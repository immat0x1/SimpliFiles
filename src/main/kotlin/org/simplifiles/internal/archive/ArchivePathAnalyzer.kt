package org.simplifiles.internal.archive

internal object ArchivePathAnalyzer {
    fun analyze(path: String): ArchivePathAnalysis {
        val isAbsolute = path.startsWith("/") ||
            path.startsWith("\\") ||
            WINDOWS_DRIVE_PATTERN.matches(path)

        if (path.isEmpty()) {
            return ArchivePathAnalysis(
                normalizedPath = null,
                isEmpty = true,
                isAbsolute = isAbsolute,
                containsParentTraversal = false,
            )
        }

        val segments = path
            .split('/', '\\')
            .filter { it.isNotEmpty() && it != "." }

        val containsParentTraversal = segments.any { it == ".." }
        val normalizedPath = if (containsParentTraversal || segments.isEmpty()) {
            null
        } else {
            segments.joinToString("/")
        }

        return ArchivePathAnalysis(
            normalizedPath = normalizedPath,
            isEmpty = normalizedPath == null && segments.isEmpty(),
            isAbsolute = isAbsolute,
            containsParentTraversal = containsParentTraversal,
        )
    }

    private val WINDOWS_DRIVE_PATTERN = Regex("^[A-Za-z]:($|[/\\\\].*)")
}

internal data class ArchivePathAnalysis(
    val normalizedPath: String?,
    val isEmpty: Boolean,
    val isAbsolute: Boolean,
    val containsParentTraversal: Boolean,
)
