package org.simplifiles.exception

import java.nio.file.Path

class ExtractionTargetException(
    path: Path,
    message: String,
) : SimplifilesException("Invalid extraction target '$path': $message")
