package org.simplifiles.internal

/**
 * Adds two non-negative values, returning [Long.MAX_VALUE] instead of wrapping around.
 */
internal fun saturatingPlus(
    current: Long,
    added: Long,
): Long = if (Long.MAX_VALUE - current < added) Long.MAX_VALUE else current + added
