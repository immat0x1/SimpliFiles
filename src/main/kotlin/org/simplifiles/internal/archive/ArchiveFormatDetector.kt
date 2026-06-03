package org.simplifiles.internal.archive

import org.simplifiles.archive.ArchiveFormat
import java.nio.file.Files
import java.nio.file.Path

internal object ArchiveFormatDetector {
    fun detect(path: Path): ArchiveFormat? {
        val signature = ByteArray(4)
        val read = Files.newInputStream(path).use { input ->
            input.read(signature)
        }

        if (read < signature.size) {
            return null
        }

        val first = signature[0].toInt() and 0xff
        val second = signature[1].toInt() and 0xff
        val third = signature[2].toInt() and 0xff
        val fourth = signature[3].toInt() and 0xff

        return if (
            first == 0x50 &&
            second == 0x4b &&
            (
                third == 0x03 && fourth == 0x04 ||
                    third == 0x05 && fourth == 0x06 ||
                    third == 0x07 && fourth == 0x08
                )
        ) {
            ArchiveFormat.ZIP
        } else {
            null
        }
    }
}
