package org.simplifiles.exception

import java.nio.file.Path

class ExtractionTargetException(
    path: Path,
    message: String,
) : SimpliFilesException("Invalid extraction target '$path': $message")
