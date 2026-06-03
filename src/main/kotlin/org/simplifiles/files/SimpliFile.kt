package org.simplifiles.files

import org.simplifiles.exception.FileOperationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * File handle for regular filesystem operations.
 */
class SimpliFile internal constructor(
    val path: Path,
) {
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

    @JvmOverloads
    fun readText(charset: Charset = Charsets.UTF_8): String = Files.readString(path, charset)

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
    fun writeText(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(
            path,
            text,
            charset,
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
        Files.writeString(
            path,
            text,
            charset,
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
        val parent = path.parent ?: Path.of(".").toAbsolutePath().normalize()
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

    fun delete(): Boolean = Files.deleteIfExists(path)

    fun copyTo(target: Path): SimpliFile {
        target.parent?.let(Files::createDirectories)
        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
        return SimpliFile(target)
    }

    fun copyTo(target: String): SimpliFile = copyTo(Path.of(target))

    fun copyTo(target: File): SimpliFile = copyTo(target.toPath())

    fun moveTo(target: Path): SimpliFile {
        target.parent?.let(Files::createDirectories)
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
        return SimpliFile(target)
    }

    fun moveTo(target: String): SimpliFile = moveTo(Path.of(target))

    fun moveTo(target: File): SimpliFile = moveTo(target.toPath())
}
