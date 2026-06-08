package org.simplifiles.files

import org.simplifiles.exception.FileOperationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime

/**
 * File handle for regular filesystem operations.
 */
class SimpliFile internal constructor(
    val path: Path,
) {
    /**
     * Java [File] view of this file handle.
     */
    val file: File
        get() = File(path.toString())

    /**
     * Java-friendly equivalent of the [file] property.
     */
    fun toFile(): File = file

    val exists: Boolean
        get() = Files.exists(path)

    /**
     * Java-friendly equivalent of the [exists] property.
     */
    fun exists(): Boolean = exists

    val size: Long
        get() = Files.size(path)

    val extension: String
        get() = path.fileName?.toString()?.substringAfterLast('.', missingDelimiterValue = "") ?: ""

    fun inputStream(): InputStream = Files.newInputStream(path)

    fun outputStream(): OutputStream {
        path.parent?.let(Files::createDirectories)
        return Files.newOutputStream(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    fun readBytes(): ByteArray = Files.readAllBytes(path)

    fun readBytes(maxBytes: Long): ByteArray {
        require(maxBytes >= 0) { "maxBytes must not be negative." }
        if (Files.size(path) > maxBytes) {
            throw FileOperationException("File exceeds read limit of $maxBytes bytes: $path")
        }
        return readBytes()
    }

    @JvmOverloads
    fun readText(charset: Charset = Charsets.UTF_8): String = readBytes().toString(charset)

    @JvmOverloads
    fun readText(
        maxBytes: Long,
        charset: Charset = Charsets.UTF_8,
    ): String = readBytes(maxBytes).toString(charset)

    @JvmOverloads
    fun readLines(
        maxBytes: Long,
        charset: Charset = Charsets.UTF_8,
    ): List<String> {
        require(maxBytes >= 0) { "maxBytes must not be negative." }
        return inputStream().use { input ->
            BoundedInputStream(input, maxBytes, path).bufferedReader(charset).use { reader ->
                reader.readLines()
            }
        }
    }

    @JvmOverloads
    fun forEachLine(
        maxBytes: Long,
        charset: Charset = Charsets.UTF_8,
        block: (String) -> Unit,
    ) {
        require(maxBytes >= 0) { "maxBytes must not be negative." }
        inputStream().use { input ->
            BoundedInputStream(input, maxBytes, path).bufferedReader(charset).useLines { lines ->
                lines.forEach(block)
            }
        }
    }

    fun writeBytes(bytes: ByteArray) {
        path.parent?.let(Files::createDirectories)
        Files.write(
            path,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    @JvmOverloads
    fun writeFrom(
        input: InputStream,
        maxBytes: Long = Long.MAX_VALUE,
    ): SimpliFile {
        path.parent?.let(Files::createDirectories)
        Files.newOutputStream(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { output ->
            copyFrom(input, output, maxBytes)
        }
        return this
    }

    @JvmOverloads
    fun writeText(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.write(
            path,
            text.toByteArray(charset),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    @JvmOverloads
    fun appendText(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.write(
            path,
            text.toByteArray(charset),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE,
        )
    }

    @JvmOverloads
    fun writeTextAtomic(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        writeBytesAtomic(text.toByteArray(charset))
    }

    fun writeBytesAtomic(bytes: ByteArray) {
        val parent = path.parent ?: Paths.get(".").toAbsolutePath().normalize()
        Files.createDirectories(parent)

        val fileName = path.fileName?.toString() ?: throw FileOperationException("File path must include a file name.")
        val temp = Files.createTempFile(parent, ".$fileName.", ".tmp")
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Throwable) {
            Files.deleteIfExists(temp)
            throw exception
        }
    }

    @JvmOverloads
    fun writeFromAtomic(
        input: InputStream,
        maxBytes: Long = Long.MAX_VALUE,
    ): SimpliFile {
        val parent = path.parent ?: Paths.get(".").toAbsolutePath().normalize()
        Files.createDirectories(parent)

        val fileName = path.fileName?.toString() ?: throw FileOperationException("File path must include a file name.")
        val temp = Files.createTempFile(parent, ".$fileName.", ".tmp")
        try {
            Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { output ->
                copyFrom(input, output, maxBytes)
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Throwable) {
            Files.deleteIfExists(temp)
            throw exception
        }
        return this
    }

    fun touch(): SimpliFile {
        if (Files.isDirectory(path)) {
            throw FileOperationException("Path is a directory: $path")
        }
        path.parent?.let(Files::createDirectories)
        Files.write(
            path,
            ByteArray(0),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()))
        return this
    }

    fun delete(): Boolean = Files.deleteIfExists(path)

    fun copyTo(target: Path): SimpliFile = copyTo(target, OverwritePolicy.REPLACE)

    fun copyTo(
        target: Path,
        overwritePolicy: OverwritePolicy,
    ): SimpliFile {
        target.parent?.let(Files::createDirectories)
        when (overwritePolicy) {
            OverwritePolicy.ERROR -> {
                if (Files.exists(target)) {
                    throw FileOperationException("Target already exists: $target")
                }
                Files.copy(path, target)
            }

            OverwritePolicy.REPLACE -> Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            OverwritePolicy.SKIP -> {
                if (!Files.exists(target)) {
                    Files.copy(path, target)
                }
            }
        }
        return SimpliFile(target)
    }

    fun copyTo(target: String): SimpliFile = copyTo(Paths.get(target))

    fun copyTo(
        target: String,
        overwritePolicy: OverwritePolicy,
    ): SimpliFile = copyTo(Paths.get(target), overwritePolicy)

    fun copyTo(target: File): SimpliFile = copyTo(Paths.get(target.path))

    fun copyTo(
        target: File,
        overwritePolicy: OverwritePolicy,
    ): SimpliFile = copyTo(Paths.get(target.path), overwritePolicy)

    fun moveTo(target: Path): SimpliFile = moveTo(target, OverwritePolicy.REPLACE)

    fun moveTo(
        target: Path,
        overwritePolicy: OverwritePolicy,
    ): SimpliFile {
        target.parent?.let(Files::createDirectories)
        when (overwritePolicy) {
            OverwritePolicy.ERROR -> {
                if (Files.exists(target)) {
                    throw FileOperationException("Target already exists: $target")
                }
                Files.move(path, target)
            }

            OverwritePolicy.REPLACE -> Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
            OverwritePolicy.SKIP -> {
                if (!Files.exists(target)) {
                    Files.move(path, target)
                }
            }
        }
        return SimpliFile(target)
    }

    fun moveTo(target: String): SimpliFile = moveTo(Paths.get(target))

    fun moveTo(
        target: String,
        overwritePolicy: OverwritePolicy,
    ): SimpliFile = moveTo(Paths.get(target), overwritePolicy)

    fun moveTo(target: File): SimpliFile = moveTo(Paths.get(target.path))

    fun moveTo(
        target: File,
        overwritePolicy: OverwritePolicy,
    ): SimpliFile = moveTo(Paths.get(target.path), overwritePolicy)

    private fun copyFrom(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
    ): Long {
        require(maxBytes >= 0) { "maxBytes must not be negative." }
        val buffer = ByteArray(DEFAULT_FILE_BUFFER_SIZE)
        var written = 0L

        while (true) {
            val remaining = maxBytes - written
            if (remaining == 0L) {
                if (input.read() < 0) {
                    return written
                }
                throw FileOperationException("Input exceeds write limit of $maxBytes bytes: $path")
            }

            val readLimit = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, readLimit)
            if (read < 0) {
                return written
            }

            output.write(buffer, 0, read)
            written += read.toLong()
        }
    }
}

private const val DEFAULT_FILE_BUFFER_SIZE: Int = 64 * 1024

private class BoundedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
    private val path: Path,
) : InputStream() {
    private var readBytes = 0L

    override fun read(): Int {
        if (readBytes == maxBytes) {
            val next = delegate.read()
            if (next < 0) {
                return -1
            }
            throw FileOperationException("File exceeds read limit of $maxBytes bytes: $path")
        }

        val next = delegate.read()
        if (next >= 0) {
            readBytes++
        }
        return next
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) {
            return 0
        }
        if (readBytes == maxBytes) {
            val next = delegate.read()
            if (next < 0) {
                return -1
            }
            throw FileOperationException("File exceeds read limit of $maxBytes bytes: $path")
        }

        val readLimit = minOf(length.toLong(), maxBytes - readBytes).toInt()
        val read = delegate.read(buffer, offset, readLimit)
        if (read > 0) {
            readBytes += read.toLong()
        }
        return read
    }

    override fun close() {
        delegate.close()
    }
}
