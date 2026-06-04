package org.simplifiles.archive

import org.simplifiles.files.OverwritePolicy

/**
 * Optional controls for saving extracted archive contents.
 */
class ArchiveSaveOptions @JvmOverloads constructor(
    val progressListener: ArchiveSaveProgressListener? = null,
    val cancellationToken: CancellationToken = CancellationToken.none(),
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val overwritePolicy: OverwritePolicy = OverwritePolicy.ERROR,
    val compressionLevel: Int = DEFAULT_COMPRESSION_LEVEL,
    val entryFilter: ArchiveEntryFilter = ArchiveEntryFilter.includeAll(),
) {
    init {
        require(bufferSize > 0) { "bufferSize must be positive." }
        require(compressionLevel in DEFAULT_COMPRESSION_LEVEL..BEST_COMPRESSION_LEVEL) {
            "compressionLevel must be between $DEFAULT_COMPRESSION_LEVEL and $BEST_COMPRESSION_LEVEL."
        }
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024
        const val DEFAULT_COMPRESSION_LEVEL: Int = -1
        const val NO_COMPRESSION_LEVEL: Int = 0
        const val BEST_SPEED_LEVEL: Int = 1
        const val BEST_COMPRESSION_LEVEL: Int = 9

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

        fun build(): ArchiveSaveOptions = ArchiveSaveOptions(
            progressListener = progressListener,
            cancellationToken = cancellationToken,
            bufferSize = bufferSize,
            overwritePolicy = overwritePolicy,
            compressionLevel = compressionLevel,
            entryFilter = entryFilter,
        )
    }
}
