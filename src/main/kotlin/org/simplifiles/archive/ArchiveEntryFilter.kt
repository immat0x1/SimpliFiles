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
    }
}
