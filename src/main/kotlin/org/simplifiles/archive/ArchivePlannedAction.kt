package org.simplifiles.archive

/**
 * Planned extraction action for a single archive entry.
 */
enum class ArchivePlannedAction(
    val writesFile: Boolean,
    val createsDirectory: Boolean,
) {
    CREATE_FILE(writesFile = true, createsDirectory = false),
    CREATE_DIRECTORY(writesFile = false, createsDirectory = true),
    SKIP_DUPLICATE(writesFile = false, createsDirectory = false),
    RENAME_DUPLICATE(writesFile = true, createsDirectory = false),
    REPLACE_DUPLICATE(writesFile = true, createsDirectory = false),
}
