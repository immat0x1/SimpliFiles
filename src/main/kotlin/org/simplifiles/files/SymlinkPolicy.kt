package org.simplifiles.files

/**
 * Decides how symbolic links are treated when a directory tree is copied, moved, or archived.
 *
 * A symbolic link can point outside the tree being processed. Following one copies the
 * target's content into the destination as a regular file, which can move data across a
 * trust boundary without the caller noticing.
 */
enum class SymlinkPolicy {
    /**
     * Leaves symbolic links out of the result.
     *
     * This is the default because it never reads through a link.
     */
    SKIP,

    /**
     * Fails when a symbolic link is found.
     */
    ERROR,

    /**
     * Reads through the link and stores the target's content as a regular file.
     *
     * Only use this for trees you control.
     */
    FOLLOW,
}
