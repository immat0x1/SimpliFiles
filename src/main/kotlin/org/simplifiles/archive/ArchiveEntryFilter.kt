package org.simplifiles.archive

/**
 * Decides whether an archive entry should be written when saving an archive.
 *
 * Entry paths use forward slashes. Directory entry paths end with `/`.
 */
fun interface ArchiveEntryFilter {
    fun include(path: String): Boolean

    companion object {
        /**
         * Returns a filter that includes every entry.
         */
        @JvmStatic
        fun includeAll(): ArchiveEntryFilter = ArchiveEntryFilter { true }

        /**
         * Returns a filter that excludes every entry.
         */
        @JvmStatic
        fun excludeAll(): ArchiveEntryFilter = ArchiveEntryFilter { false }

        /**
         * Returns a filter that includes entries whose paths start with [prefix].
         */
        @JvmStatic
        fun pathStartsWith(prefix: String): ArchiveEntryFilter {
            val normalizedPrefix = normalizePathFragment(prefix)

            return ArchiveEntryFilter { path -> path.startsWith(normalizedPrefix) }
        }

        /**
         * Returns a filter that includes entries whose paths end with [suffix].
         */
        @JvmStatic
        fun pathEndsWith(suffix: String): ArchiveEntryFilter {
            val normalizedSuffix = normalizePathFragment(suffix)

            return ArchiveEntryFilter { path -> path.endsWith(normalizedSuffix) }
        }

        /**
         * Returns a filter that includes entries rejected by [filter].
         */
        @JvmStatic
        fun not(filter: ArchiveEntryFilter): ArchiveEntryFilter =
            ArchiveEntryFilter { path -> !filter.include(path) }

        /**
         * Returns a filter that includes entries accepted by every provided filter.
         */
        @JvmStatic
        fun allOf(vararg filters: ArchiveEntryFilter): ArchiveEntryFilter =
            ArchiveEntryFilter { path -> filters.all { filter -> filter.include(path) } }

        /**
         * Returns a filter that includes entries accepted by at least one provided filter.
         */
        @JvmStatic
        fun anyOf(vararg filters: ArchiveEntryFilter): ArchiveEntryFilter =
            ArchiveEntryFilter { path -> filters.any { filter -> filter.include(path) } }

        private fun normalizePathFragment(path: String): String = path.replace('\\', '/')
    }
}
