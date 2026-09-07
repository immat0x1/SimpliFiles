package org.simplifiles.internal.archive

import org.simplifiles.archive.ArchiveFormat
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal object ArchiveFormatDetector {
    private const val SIGNATURE_LENGTH = 4

    fun detect(path: Path): ArchiveFormat? {
        val signature = Files.newInputStream(path).use(::readSignature) ?: return null

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

    /**
     * Reads exactly [SIGNATURE_LENGTH] bytes, or returns null when the stream ends first.
     *
     * A single [InputStream.read] may return fewer bytes than requested even when more are
     * available, so a short read must not be mistaken for a short file.
     */
    internal fun readSignature(input: InputStream): ByteArray? {
        val signature = ByteArray(SIGNATURE_LENGTH)
        var offset = 0

        while (offset < signature.size) {
            val read = input.read(signature, offset, signature.size - offset)
            if (read < 0) {
                return null
            }
            offset += read
        }

        return signature
    }
}
