package org.simplifiles.exception

import java.nio.file.Path

class UnsupportedArchiveFormatException(
    path: Path,
) : SimplifilesException("Unsupported archive format: $path")
