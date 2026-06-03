package org.simplifiles.exception

import java.nio.file.Path

class ArchiveWriteException(
    path: Path,
    message: String,
) : SimplifilesException("Cannot write archive '$path': $message")
