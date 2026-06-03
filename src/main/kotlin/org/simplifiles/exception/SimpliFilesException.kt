package org.simplifiles.exception

import java.io.IOException

open class SimpliFilesException : IOException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
