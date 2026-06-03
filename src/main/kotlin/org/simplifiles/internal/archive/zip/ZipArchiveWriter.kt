package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.archive.ArchiveSaveProgress
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveWriteException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

internal object ZipArchiveWriter {
    fun write(
        root: Path,
        output: Path,
        options: ArchiveSaveOptions,
    ) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedOutput = output.toAbsolutePath().normalize()

        if (normalizedOutput.startsWith(normalizedRoot)) {
            throw ArchiveWriteException(output, "output path must be outside extracted archive root")
        }

        if (Files.exists(normalizedOutput)) {
            throw ArchiveWriteException(output, "output file already exists")
        }

        checkCanceled(options)
        normalizedOutput.parent?.let { Files.createDirectories(it) }
        val directories = listDirectories(normalizedRoot)
        val files = listFiles(normalizedRoot)
        val progress = SaveProgress(
            options = options,
            totalEntries = (directories.size + files.size).toLong(),
            totalBytes = totalFileSize(files),
        )

        try {
            progress.emit(currentEntryPath = null)
            ZipOutputStream(
                Files.newOutputStream(
                    normalizedOutput,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                ),
            ).use { zip ->
                writeDirectories(zip, normalizedRoot, directories, progress)
                writeFiles(zip, normalizedRoot, files, progress, options.bufferSize)
            }
        } catch (exception: Throwable) {
            Files.deleteIfExists(normalizedOutput)
            throw exception
        }
    }

    private fun writeDirectories(
        zip: ZipOutputStream,
        normalizedRoot: Path,
        directories: List<Path>,
        progress: SaveProgress,
    ) {
        for (directory in directories) {
            progress.checkCanceled()
            val archivePath = normalizedRoot.relativize(directory).toString().replace('\\', '/') + "/"
            zip.putNextEntry(ZipEntry(archivePath))
            zip.closeEntry()
            progress.entryCompleted(archivePath)
        }
    }

    private fun writeFiles(
        zip: ZipOutputStream,
        normalizedRoot: Path,
        files: List<Path>,
        progress: SaveProgress,
        bufferSize: Int,
    ) {
        for (file in files) {
            progress.checkCanceled()
            val archivePath = normalizedRoot.relativize(file).toString().replace('\\', '/')
            zip.putNextEntry(ZipEntry(archivePath))
            Files.newInputStream(file).use { input ->
                copy(input, zip, bufferSize, progress, archivePath)
            }
            zip.closeEntry()
            progress.entryCompleted(archivePath)
        }
    }

    private fun listDirectories(normalizedRoot: Path): List<Path> =
        Files.walk(normalizedRoot).use { stream ->
            stream.asSequence()
                .filter { it != normalizedRoot && Files.isDirectory(it) }
                .sortedBy { normalizedRoot.relativize(it).toString() }
                .toList()
        }

    private fun listFiles(normalizedRoot: Path): List<Path> =
        Files.walk(normalizedRoot).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .sortedBy { normalizedRoot.relativize(it).toString() }
                .toList()
        }

    private fun copy(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int,
        progress: SaveProgress,
        archivePath: String,
    ) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            progress.checkCanceled()
            val read = input.read(buffer)
            if (read < 0) {
                return
            }
            output.write(buffer, 0, read)
            progress.bytesWritten(read.toLong(), archivePath)
        }
    }

    private fun totalFileSize(files: List<Path>): Long =
        files.fold(0L) { total, file ->
            val size = Files.size(file)
            if (Long.MAX_VALUE - total < size) {
                Long.MAX_VALUE
            } else {
                total + size
            }
        }

    private fun checkCanceled(options: ArchiveSaveOptions) {
        if (options.cancellationToken.isCancellationRequested()) {
            throw ArchiveOperationCanceledException()
        }
    }

    private class SaveProgress(
        private val options: ArchiveSaveOptions,
        private val totalEntries: Long,
        private val totalBytes: Long,
    ) {
        private var entriesProcessed: Long = 0
        private var bytesWritten: Long = 0

        fun checkCanceled() {
            if (options.cancellationToken.isCancellationRequested()) {
                throw ArchiveOperationCanceledException()
            }
        }

        fun bytesWritten(bytes: Long, currentEntryPath: String) {
            bytesWritten += bytes
            emit(currentEntryPath)
        }

        fun entryCompleted(currentEntryPath: String) {
            entriesProcessed += 1
            emit(currentEntryPath)
        }

        fun emit(currentEntryPath: String?) {
            options.progressListener?.onProgress(
                ArchiveSaveProgress(
                    currentEntryPath = currentEntryPath,
                    entriesProcessed = entriesProcessed,
                    totalEntries = totalEntries,
                    bytesWritten = bytesWritten,
                    totalBytes = totalBytes,
                ),
            )
        }
    }
}
