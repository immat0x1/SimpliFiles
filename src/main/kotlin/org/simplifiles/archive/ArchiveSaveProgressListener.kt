package org.simplifiles.archive

/**
 * Receives save progress snapshots.
 */
fun interface ArchiveSaveProgressListener {
    fun onProgress(progress: ArchiveSaveProgress)
}
