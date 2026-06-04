package org.simplifiles.internal.archive

internal object ArchivePathAnalyzer {
    fun analyze(path: String): ArchivePathAnalysis {
        val isAbsolute = path.startsWith("/") ||
            path.startsWith("\\") ||
            isWindowsDriveAbsolute(path)

        if (path.isEmpty()) {
            return ArchivePathAnalysis(
                normalizedPath = null,
                isEmpty = true,
                isAbsolute = isAbsolute,
                containsParentTraversal = false,
            )
        }

        var containsParentTraversal = false
        var sawSegment = false
        val normalized = StringBuilder(path.length)
        var segmentStart = 0

        for (index in 0..path.length) {
            if (index < path.length && path[index] != '/' && path[index] != '\\') {
                continue
            }

            val segmentLength = index - segmentStart
            if (segmentLength > 0 && !isCurrentDirectorySegment(path, segmentStart, segmentLength)) {
                sawSegment = true
                if (isParentDirectorySegment(path, segmentStart, segmentLength)) {
                    containsParentTraversal = true
                } else if (!containsParentTraversal) {
                    if (normalized.isNotEmpty()) {
                        normalized.append('/')
                    }
                    normalized.append(path, segmentStart, index)
                }
            }
            segmentStart = index + 1
        }

        val normalizedPath = if (containsParentTraversal || normalized.isEmpty()) {
            null
        } else {
            normalized.toString()
        }

        return ArchivePathAnalysis(
            normalizedPath = normalizedPath,
            isEmpty = !containsParentTraversal && !sawSegment,
            isAbsolute = isAbsolute,
            containsParentTraversal = containsParentTraversal,
        )
    }

    private fun isCurrentDirectorySegment(
        path: String,
        start: Int,
        length: Int,
    ): Boolean = length == 1 && path[start] == '.'

    private fun isParentDirectorySegment(
        path: String,
        start: Int,
        length: Int,
    ): Boolean = length == 2 && path[start] == '.' && path[start + 1] == '.'

    private fun isWindowsDriveAbsolute(path: String): Boolean =
        path.length >= 2 &&
            path[1] == ':' &&
            path[0].isLetter() &&
            (path.length == 2 || path[2] == '/' || path[2] == '\\')
}

internal data class ArchivePathAnalysis(
    val normalizedPath: String?,
    val isEmpty: Boolean,
    val isAbsolute: Boolean,
    val containsParentTraversal: Boolean,
)
