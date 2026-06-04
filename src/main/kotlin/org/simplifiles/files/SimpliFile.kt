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
}
