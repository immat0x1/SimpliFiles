package org.simplifiles.archive

/**
 * Progress snapshot emitted while saving extracted archive contents.
 */
data class ArchiveSaveProgress @JvmOverloads constructor(
    val currentEntryPath: String? = null,
    val entriesProcessed: Long = 0,
    val totalEntries: Long = 0,
    val bytesWritten: Long = 0,
    val totalBytes: Long = 0,
) {
    val isComplete: Boolean
        get() = entriesProcessed >= totalEntries && bytesWritten >= totalBytes
}
