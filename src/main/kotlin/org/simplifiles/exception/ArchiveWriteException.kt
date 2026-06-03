package org.simplifiles.exception

import java.nio.file.Path

class ArchiveWriteException(
    path: Path,
    message: String,
) : SimpliFilesException("Cannot write archive '$path': $message")
