package org.simplifiles.archive

/**
 * Optional controls for archive extraction.
 */
class ArchiveExtractionOptions @JvmOverloads constructor(
    val progressListener: ArchiveProgressListener? = null,
    val cancellationToken: CancellationToken = CancellationToken.none(),
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val targetPolicy: ExtractionTargetPolicy = ExtractionTargetPolicy.ERROR_IF_NOT_EMPTY,
) {
    init {
        require(bufferSize > 0) { "bufferSize must be positive." }
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024

        /**
         * Returns extraction options with no progress listener and no cancellation.
         */
        @JvmStatic
        fun defaults(): ArchiveExtractionOptions = ArchiveExtractionOptions()

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
     * Java-friendly builder for [ArchiveExtractionOptions].
     */
    class Builder internal constructor(
        options: ArchiveExtractionOptions = defaults(),
    ) {
        private var progressListener: ArchiveProgressListener? = options.progressListener
        private var cancellationToken: CancellationToken = options.cancellationToken
        private var bufferSize: Int = options.bufferSize
        private var targetPolicy: ExtractionTargetPolicy = options.targetPolicy

        fun progressListener(listener: ArchiveProgressListener?): Builder = apply {
            progressListener = listener
        }

        fun cancellationToken(token: CancellationToken): Builder = apply {
            cancellationToken = token
        }

        fun bufferSize(value: Int): Builder = apply {
            bufferSize = value
        }

        fun targetPolicy(policy: ExtractionTargetPolicy): Builder = apply {
            targetPolicy = policy
        }

        fun build(): ArchiveExtractionOptions = ArchiveExtractionOptions(
            progressListener = progressListener,
            cancellationToken = cancellationToken,
            bufferSize = bufferSize,
            targetPolicy = targetPolicy,
        )
    }
}
