package org.simplifiles.archive

import org.simplifiles.files.OverwritePolicy
import org.simplifiles.files.SymlinkPolicy

/**
 * Optional controls for saving extracted archive contents.
 *
 * Symbolic links found while walking a source directory are skipped by default. See [SymlinkPolicy].
 *
 * Entries keep the last modified time of their source file by default. Set [entryTimestamp] to a
 * fixed value when the same input tree must always produce the same archive bytes.
 */
class ArchiveSaveOptions @JvmOverloads constructor(
    val progressListener: ArchiveSaveProgressListener? = null,
    val cancellationToken: CancellationToken = CancellationToken.none(),
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val overwritePolicy: OverwritePolicy = OverwritePolicy.ERROR,
    val compressionLevel: Int = DEFAULT_COMPRESSION_LEVEL,
    val entryFilter: ArchiveEntryFilter = ArchiveEntryFilter.includeAll(),
    val symlinkPolicy: SymlinkPolicy = SymlinkPolicy.SKIP,
    val entryTimestamp: Long = PRESERVE_SOURCE_TIMESTAMP,
) {
    init {
        require(bufferSize > 0) { "bufferSize must be positive." }
        require(compressionLevel in DEFAULT_COMPRESSION_LEVEL..BEST_COMPRESSION_LEVEL) {
            "compressionLevel must be between $DEFAULT_COMPRESSION_LEVEL and $BEST_COMPRESSION_LEVEL."
        }
        require(entryTimestamp == PRESERVE_SOURCE_TIMESTAMP || entryTimestamp >= 0) {
            "entryTimestamp must be PRESERVE_SOURCE_TIMESTAMP or a non-negative epoch millisecond value."
        }
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024
        const val DEFAULT_COMPRESSION_LEVEL: Int = -1
        const val NO_COMPRESSION_LEVEL: Int = 0
        const val BEST_SPEED_LEVEL: Int = 1
        const val BEST_COMPRESSION_LEVEL: Int = 9

        /**
         * Marker for [entryTimestamp] that keeps each entry's own source modification time.
         */
        const val PRESERVE_SOURCE_TIMESTAMP: Long = -1L

        /**
         * Returns save options with no progress listener and no cancellation.
         */
        @JvmStatic
        fun defaults(): ArchiveSaveOptions = ArchiveSaveOptions()

        /**
         * Creates a Java-friendly options builder.
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    override fun toString(): String =
        "ArchiveSaveOptions(bufferSize=$bufferSize, overwritePolicy=$overwritePolicy, " +
            "compressionLevel=$compressionLevel, symlinkPolicy=$symlinkPolicy, " +
            "entryTimestamp=$entryTimestamp, hasProgressListener=${progressListener != null})"

    /**
     * Creates a builder initialized with this options object's values.
     */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Java-friendly builder for [ArchiveSaveOptions].
     */
    class Builder internal constructor(
        options: ArchiveSaveOptions = defaults(),
    ) {
        private var progressListener: ArchiveSaveProgressListener? = options.progressListener
        private var cancellationToken: CancellationToken = options.cancellationToken
        private var bufferSize: Int = options.bufferSize
        private var overwritePolicy: OverwritePolicy = options.overwritePolicy
        private var compressionLevel: Int = options.compressionLevel
        private var entryFilter: ArchiveEntryFilter = options.entryFilter
        private var symlinkPolicy: SymlinkPolicy = options.symlinkPolicy
        private var entryTimestamp: Long = options.entryTimestamp

        fun progressListener(listener: ArchiveSaveProgressListener?): Builder = apply {
            progressListener = listener
        }

        fun cancellationToken(token: CancellationToken): Builder = apply {
            cancellationToken = token
        }

        fun bufferSize(value: Int): Builder = apply {
            bufferSize = value
        }

        fun overwritePolicy(policy: OverwritePolicy): Builder = apply {
            overwritePolicy = policy
        }

        fun compressionLevel(value: Int): Builder = apply {
            compressionLevel = value
        }

        fun entryFilter(filter: ArchiveEntryFilter): Builder = apply {
            entryFilter = filter
        }

        fun symlinkPolicy(policy: SymlinkPolicy): Builder = apply {
            symlinkPolicy = policy
        }

        /**
         * Sets a fixed last modified time, in epoch milliseconds, for every written entry.
         *
         * Pass [PRESERVE_SOURCE_TIMESTAMP] to keep each source file's own time instead.
         */
        fun entryTimestamp(value: Long): Builder = apply {
            entryTimestamp = value
        }

        fun build(): ArchiveSaveOptions = ArchiveSaveOptions(
            progressListener = progressListener,
            cancellationToken = cancellationToken,
            bufferSize = bufferSize,
            overwritePolicy = overwritePolicy,
            compressionLevel = compressionLevel,
            entryFilter = entryFilter,
            symlinkPolicy = symlinkPolicy,
            entryTimestamp = entryTimestamp,
        )
    }
}
