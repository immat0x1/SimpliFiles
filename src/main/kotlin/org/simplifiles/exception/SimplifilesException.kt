package org.simplifiles.exception

import java.io.IOException

open class SimplifilesException : IOException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
