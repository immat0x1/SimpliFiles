package org.simplifiles.archive

/**
 * Optional controls for saving extracted archive contents.
 */
class ArchiveSaveOptions @JvmOverloads constructor(
    val progressListener: ArchiveSaveProgressListener? = null,
    val cancellationToken: CancellationToken = CancellationToken.none(),
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {
    init {
        require(bufferSize > 0) { "bufferSize must be positive." }
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024

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

        fun progressListener(listener: ArchiveSaveProgressListener?): Builder = apply {
            progressListener = listener
        }

        fun cancellationToken(token: CancellationToken): Builder = apply {
            cancellationToken = token
        }

        fun bufferSize(value: Int): Builder = apply {
            bufferSize = value
        }

        fun build(): ArchiveSaveOptions = ArchiveSaveOptions(
            progressListener = progressListener,
            cancellationToken = cancellationToken,
            bufferSize = bufferSize,
        )
    }
}
