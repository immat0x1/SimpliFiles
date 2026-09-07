package org.simplifiles.files

/**
 * Optional controls for copying or moving directory trees.
 *
 * Symbolic links inside the source tree are skipped by default. See [SymlinkPolicy].
 */
class DirectoryTransferOptions @JvmOverloads constructor(
    val overwritePolicy: DirectoryOverwritePolicy = DirectoryOverwritePolicy.ERROR,
    val maxFiles: Long = Long.MAX_VALUE,
    val maxBytes: Long = Long.MAX_VALUE,
    val symlinkPolicy: SymlinkPolicy = SymlinkPolicy.SKIP,
) {
    init {
        require(maxFiles >= 0) { "maxFiles must not be negative." }
        require(maxBytes >= 0) { "maxBytes must not be negative." }
    }

    companion object {
        /**
         * Returns directory transfer options that fail when the target exists, skip symbolic links,
         * and do not impose extra limits.
         */
        @JvmStatic
        fun defaults(): DirectoryTransferOptions = DirectoryTransferOptions()

        /**
         * Creates a Java-friendly options builder.
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    override fun toString(): String =
        "DirectoryTransferOptions(overwritePolicy=$overwritePolicy, maxFiles=$maxFiles, " +
            "maxBytes=$maxBytes, symlinkPolicy=$symlinkPolicy)"

    /**
     * Creates a builder initialized with this options object's values.
     */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Java-friendly builder for [DirectoryTransferOptions].
     */
    class Builder internal constructor(
        options: DirectoryTransferOptions = defaults(),
    ) {
        private var overwritePolicy: DirectoryOverwritePolicy = options.overwritePolicy
        private var maxFiles: Long = options.maxFiles
        private var maxBytes: Long = options.maxBytes
        private var symlinkPolicy: SymlinkPolicy = options.symlinkPolicy

        fun overwritePolicy(policy: DirectoryOverwritePolicy): Builder = apply {
            overwritePolicy = policy
        }

        fun maxFiles(value: Long): Builder = apply {
            maxFiles = value
        }

        fun maxBytes(value: Long): Builder = apply {
            maxBytes = value
        }

        fun symlinkPolicy(policy: SymlinkPolicy): Builder = apply {
            symlinkPolicy = policy
        }

        fun build(): DirectoryTransferOptions = DirectoryTransferOptions(
            overwritePolicy = overwritePolicy,
            maxFiles = maxFiles,
            maxBytes = maxBytes,
            symlinkPolicy = symlinkPolicy,
        )
    }
}
