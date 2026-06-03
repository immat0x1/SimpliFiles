package org.simplifiles.archive

import java.util.function.BooleanSupplier

/**
 * Reports whether an archive operation should stop.
 */
fun interface CancellationToken {
    fun isCancellationRequested(): Boolean

    companion object {
        /**
         * Returns a token that never cancels.
         */
        @JvmStatic
        fun none(): CancellationToken = CancellationToken { false }

        /**
         * Creates a token backed by a Java [BooleanSupplier].
         */
        @JvmStatic
        fun fromSupplier(supplier: BooleanSupplier): CancellationToken =
            CancellationToken { supplier.asBoolean }
    }
}
