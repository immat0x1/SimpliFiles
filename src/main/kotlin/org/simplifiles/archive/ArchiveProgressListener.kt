package org.simplifiles.archive

/**
 * Receives extraction progress snapshots.
 */
fun interface ArchiveProgressListener {
    fun onProgress(progress: ArchiveProgress)
}
