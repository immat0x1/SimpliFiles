package org.simplifiles.archive

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class ArchiveSizeOverflowTest {
    private fun entry(uncompressedSize: Long) = ArchiveEntryInfo(
        path = "big-$uncompressedSize.bin",
        normalizedPath = "big-$uncompressedSize.bin",
        isDirectory = false,
        compressedSize = 1,
        uncompressedSize = uncompressedSize,
        compressionMethod = 8,
    )

    private fun plannedEntry(uncompressedSize: Long) = ArchivePlannedEntry(
        sourcePath = "big-$uncompressedSize.bin",
        normalizedPath = "big-$uncompressedSize.bin",
        destinationPath = Paths.get("out", "big-$uncompressedSize.bin"),
        action = ArchivePlannedAction.CREATE_FILE,
        isDirectory = false,
        compressedSize = 1,
        uncompressedSize = uncompressedSize,
    )

    @Test
    fun `inspection size saturates instead of wrapping negative`() {
        val inspection = ArchiveInspection(
            format = ArchiveFormat.ZIP,
            entries = listOf(entry(Long.MAX_VALUE), entry(Long.MAX_VALUE)),
        )

        assertEquals(Long.MAX_VALUE, inspection.totalKnownUncompressedSize)
    }

    @Test
    fun `inspection size still adds normal values`() {
        val inspection = ArchiveInspection(
            format = ArchiveFormat.ZIP,
            entries = listOf(entry(10), entry(32)),
        )

        assertEquals(42, inspection.totalKnownUncompressedSize)
    }

    @Test
    fun `plan size saturates instead of wrapping negative`() {
        val plan = ArchiveExtractionPlan(
            format = ArchiveFormat.ZIP,
            targetRoot = Paths.get("out"),
            entries = listOf(plannedEntry(Long.MAX_VALUE), plannedEntry(Long.MAX_VALUE)),
            validationReport = ValidationReport(format = ArchiveFormat.ZIP),
        )

        assertEquals(Long.MAX_VALUE, plan.totalBytesToWrite)
    }

    @Test
    fun `plan size still adds normal values`() {
        val plan = ArchiveExtractionPlan(
            format = ArchiveFormat.ZIP,
            targetRoot = Paths.get("out"),
            entries = listOf(plannedEntry(10), plannedEntry(32)),
            validationReport = ValidationReport(format = ArchiveFormat.ZIP),
        )

        assertEquals(42, plan.totalBytesToWrite)
    }
}
