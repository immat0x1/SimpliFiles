package org.simplifiles.archive

import org.simplifiles.internal.archive.ArchivePathResolver
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * File handle inside an extracted archive session.
 *
 * The file path is always relative to the archive root and uses `/` separators.
 */
class ArchiveFile internal constructor(
    private val root: Path,
    val path: String,
    val absolutePath: Path,
) {
    /**
     * Java [File] view of this extracted archive file.
     */
    val file: File
        get() = File(absolutePath.toString())

    /**
     * Java-friendly equivalent of the [file] property.
     */
    fun toFile(): File = file

    val exists: Boolean
        get() = Files.exists(absolutePath)

    /**
     * Java-friendly equivalent of the [exists] property.
     */
    fun exists(): Boolean = exists

    val size: Long
        get() = Files.size(absolutePath)

    val extension: String
        get() = path.substringAfterLast('.', missingDelimiterValue = "")

    /**
     * Opens this file for streaming reads.
     */
    fun inputStream(): InputStream = Files.newInputStream(absolutePath)

    /**
     * Reads the full file into memory.
     */
    fun readBytes(): ByteArray = Files.readAllBytes(absolutePath)

    /**
     * Reads this file as text.
     */
    @JvmOverloads
    fun readText(charset: Charset = Charsets.UTF_8): String = readBytes().toString(charset)

    /**
     * Replaces this file with the given text, creating parent directories if needed.
     */
    @JvmOverloads
    fun writeText(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        Files.createDirectories(absolutePath.parent)
        Files.write(
            absolutePath,
            text.toByteArray(charset),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    /**
     * Appends text to this file, creating parent directories and the file if needed.
     */
    @JvmOverloads
    fun appendText(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        Files.createDirectories(absolutePath.parent)
        Files.write(
            absolutePath,
            text.toByteArray(charset),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE,
        )
    }

    /**
     * Deletes this file if it exists.
     */
    fun delete(): Boolean = Files.deleteIfExists(absolutePath)

    /**
     * Copies this file to another path inside the same extracted archive root.
     */
    fun copyTo(path: String): ArchiveFile {
        val target = ArchivePathResolver.resolve(root, path)
        Files.createDirectories(target.parent)
        Files.copy(absolutePath, target, StandardCopyOption.REPLACE_EXISTING)

        return ArchiveFile(
            root = root,
            path = root.relativize(target).toString().replace('\\', '/'),
            absolutePath = target,
        )
    }

    /**
     * Moves this file to another path inside the same extracted archive root.
     */
    fun moveTo(path: String): ArchiveFile {
        val target = ArchivePathResolver.resolve(root, path)
        Files.createDirectories(target.parent)
        Files.move(absolutePath, target, StandardCopyOption.REPLACE_EXISTING)

        return ArchiveFile(
            root = root,
            path = root.relativize(target).toString().replace('\\', '/'),
            absolutePath = target,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is ArchiveFile && root == other.root && absolutePath == other.absolutePath)

    override fun hashCode(): Int = 31 * root.hashCode() + absolutePath.hashCode()

    override fun toString(): String = "ArchiveFile(path=$path, absolutePath=$absolutePath)"
}
