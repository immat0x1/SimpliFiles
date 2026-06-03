package org.simplifiles.exception

import java.nio.file.Path

class UnsupportedArchiveFormatException(
    path: Path,
) : SimpliFilesException("Unsupported archive format: $path")
