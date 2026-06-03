package org.simplifiles.exception

class UnsafeArchivePathException(
    path: String,
    reason: String,
) : SimplifilesException("Unsafe archive path '$path': $reason")
