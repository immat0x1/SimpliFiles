package org.simplifiles.archive

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressStateTest {
    @Test
    fun `archive progress completion requires entries and bytes`() {
        assertFalse(
            ArchiveProgress(
                entriesProcessed = 1,
                totalEntries = 2,
                bytesWritten = 10,
                totalBytes = 10,
            ).isComplete,
        )
        assertFalse(
            ArchiveProgress(
                entriesProcessed = 2,
                totalEntries = 2,
                bytesWritten = 9,
                totalBytes = 10,
            ).isComplete,
        )
        assertTrue(
            ArchiveProgress(
                entriesProcessed = 2,
                totalEntries = 2,
                bytesWritten = 10,
                totalBytes = 10,
            ).isComplete,
        )
    }

    @Test
    fun `archive save progress completion requires entries and bytes`() {
        assertFalse(
            ArchiveSaveProgress(
                entriesProcessed = 1,
                totalEntries = 2,
                bytesWritten = 10,
                totalBytes = 10,
            ).isComplete,
        )
        assertFalse(
            ArchiveSaveProgress(
                entriesProcessed = 2,
                totalEntries = 2,
                bytesWritten = 9,
                totalBytes = 10,
            ).isComplete,
        )
        assertTrue(
            ArchiveSaveProgress(
                entriesProcessed = 2,
                totalEntries = 2,
                bytesWritten = 10,
                totalBytes = 10,
            ).isComplete,
        )
    }
}
