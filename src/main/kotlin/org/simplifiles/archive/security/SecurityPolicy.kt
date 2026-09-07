package org.simplifiles.archive.security

/**
 * Security limits applied during archive validation and extraction.
 *
 * The default strict policy is intended for untrusted archives.
 *
 * [maxEntries], [maxTotalUncompressedSize], [maxSingleFileSize], [maxCompressionRatio],
 * [allowAbsolutePaths], and [duplicatePolicy] are enforced during validation and again while
 * bytes are written.
 *
 * [maxNestedArchiveDepth], [allowSymlinks], and [allowHardlinks] are reserved. Their default
 * values describe what SimpliFiles already does, but raising them has no effect yet. See the
 * documentation on each one.
 */
data class SecurityPolicy @JvmOverloads constructor(
    val maxEntries: Long = 10_000,
    val maxTotalUncompressedSize: Long = 1_000_000_000,
    val maxSingleFileSize: Long = 100_000_000,
    val maxCompressionRatio: Double = 100.0,
    /**
     * Reserved, not enforced yet.
     *
     * SimpliFiles never extracts archives found inside an archive, so the default `0` already
     * holds. A larger value does not enable nested extraction.
     */
    val maxNestedArchiveDepth: Int = 0,
    /**
     * Reserved, not enforced yet.
     *
     * ZIP extraction only ever writes regular files, so the default `false` already holds.
     * Setting `true` does not make extraction recreate symbolic links, because
     * `java.util.zip.ZipEntry` does not expose the external attributes that mark them.
     *
     * This is unrelated to writing archives: see `SymlinkPolicy` for how links in a source
     * tree are handled by `zipTo` and directory copies.
     */
    val allowSymlinks: Boolean = false,
    /**
     * Reserved, not enforced yet.
     *
     * ZIP extraction only ever writes regular files, so the default `false` already holds.
     * Setting `true` does not make extraction recreate hard links.
     */
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

        /**
         * Reserved, not enforced yet. See [SecurityPolicy.maxNestedArchiveDepth].
         */
        fun maxNestedArchiveDepth(value: Int): Builder = apply {
            maxNestedArchiveDepth = value
        }

        /**
         * Reserved, not enforced yet. See [SecurityPolicy.allowSymlinks].
         */
        fun allowSymlinks(value: Boolean): Builder = apply {
            allowSymlinks = value
        }

        /**
         * Reserved, not enforced yet. See [SecurityPolicy.allowHardlinks].
         */
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
