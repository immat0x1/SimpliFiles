package org.simplifiles.archive.security

/**
 * Security limits applied during archive validation and extraction.
 *
 * The default strict policy is intended for untrusted archives.
 */
data class SecurityPolicy @JvmOverloads constructor(
    val maxEntries: Long = 10_000,
    val maxTotalUncompressedSize: Long = 1_000_000_000,
    val maxSingleFileSize: Long = 100_000_000,
    val maxCompressionRatio: Double = 100.0,
    val maxNestedArchiveDepth: Int = 0,
    val allowSymlinks: Boolean = false,
    val allowHardlinks: Boolean = false,
    val allowAbsolutePaths: Boolean = false,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.ERROR,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive." }
        require(maxTotalUncompressedSize > 0) { "maxTotalUncompressedSize must be positive." }
        require(maxSingleFileSize > 0) { "maxSingleFileSize must be positive." }
        require(maxCompressionRatio > 0.0) { "maxCompressionRatio must be positive." }
        require(maxNestedArchiveDepth >= 0) { "maxNestedArchiveDepth must not be negative." }
    }

    companion object {
        /**
         * Returns the default strict policy.
         */
        @JvmStatic
        fun strict(): SecurityPolicy = SecurityPolicy()

        /**
         * Creates a Java-friendly builder starting from the strict policy.
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * Creates a builder initialized with this policy's values.
     */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Java-friendly builder for [SecurityPolicy].
     */
    class Builder internal constructor(
        policy: SecurityPolicy = strict(),
    ) {
        private var maxEntries: Long = policy.maxEntries
        private var maxTotalUncompressedSize: Long = policy.maxTotalUncompressedSize
        private var maxSingleFileSize: Long = policy.maxSingleFileSize
        private var maxCompressionRatio: Double = policy.maxCompressionRatio
        private var maxNestedArchiveDepth: Int = policy.maxNestedArchiveDepth
        private var allowSymlinks: Boolean = policy.allowSymlinks
        private var allowHardlinks: Boolean = policy.allowHardlinks
        private var allowAbsolutePaths: Boolean = policy.allowAbsolutePaths
        private var duplicatePolicy: DuplicatePolicy = policy.duplicatePolicy

        fun maxEntries(value: Long): Builder = apply {
            maxEntries = value
        }

        fun maxTotalUncompressedSize(value: Long): Builder = apply {
            maxTotalUncompressedSize = value
        }

        fun maxSingleFileSize(value: Long): Builder = apply {
            maxSingleFileSize = value
        }

        fun maxCompressionRatio(value: Double): Builder = apply {
            maxCompressionRatio = value
        }

        fun maxNestedArchiveDepth(value: Int): Builder = apply {
            maxNestedArchiveDepth = value
        }

        fun allowSymlinks(value: Boolean): Builder = apply {
            allowSymlinks = value
        }

        fun allowHardlinks(value: Boolean): Builder = apply {
            allowHardlinks = value
        }

        fun allowAbsolutePaths(value: Boolean): Builder = apply {
            allowAbsolutePaths = value
        }

        fun duplicatePolicy(value: DuplicatePolicy): Builder = apply {
            duplicatePolicy = value
        }

        fun build(): SecurityPolicy = SecurityPolicy(
            maxEntries = maxEntries,
            maxTotalUncompressedSize = maxTotalUncompressedSize,
            maxSingleFileSize = maxSingleFileSize,
            maxCompressionRatio = maxCompressionRatio,
            maxNestedArchiveDepth = maxNestedArchiveDepth,
            allowSymlinks = allowSymlinks,
            allowHardlinks = allowHardlinks,
            allowAbsolutePaths = allowAbsolutePaths,
            duplicatePolicy = duplicatePolicy,
        )
    }
}
