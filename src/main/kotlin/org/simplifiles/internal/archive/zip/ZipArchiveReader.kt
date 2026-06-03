package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveFormat
import org.simplifiles.archive.ArchiveInspection
import org.simplifiles.exception.CorruptedArchiveException
import org.simplifiles.internal.archive.ArchivePathAnalyzer
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

internal object ZipArchiveReader {
    fun inspect(path: Path): ArchiveInspection {
        val entries = try {
            ZipFile(path.toFile()).use { zipFile ->
                zipFile.entries().asSequence().map { entry ->
                    val pathAnalysis = ArchivePathAnalyzer.analyze(entry.name)

                    ArchiveEntryInfo(
                        path = entry.name,
                        normalizedPath = pathAnalysis.normalizedPath,
                        isDirectory = entry.isDirectory,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size,
                        compressionMethod = entry.method,
                    )
                }.toList()
            }
        } catch (exception: ZipException) {
            throw CorruptedArchiveException(path, exception)
        }

        return ArchiveInspection(
            format = ArchiveFormat.ZIP,
            entries = entries,
        )
    }
}
