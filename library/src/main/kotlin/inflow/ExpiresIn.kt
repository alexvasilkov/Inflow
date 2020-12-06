package inflow

import inflow.ExpiresIn.Companion.Duration
import inflow.ExpiresIn.Companion.IfNull
import inflow.utils.now

/**
 * Expiration time provider to control when the data should be automatically refreshed.
 *
 * This interface defines a set of built-in providers: [IfNull], [Duration], and
 * supports custom providers by implementing this interface's [expiresIn] function.
 */
@Suppress("FunctionName") // Mimicking sealed class but allowing extensions
interface ExpiresIn<T> {
    /**
     * Relative time in milliseconds after which given data will expire and should be automatically
     * refreshed. In other words, how many milliseconds (from now) the data can be considered fresh.
     *
     * Return `0L` if the data should be refreshed immediately.
     *
     * Return `Long.MAX_VALUE` if the data should never be refreshed automatically.
     */
    fun expiresIn(data: T): Long

    companion object {
        private val ifNull = object : ExpiresIn<Any?> {
            override fun expiresIn(data: Any?) = if (data == null) 0L else Long.MAX_VALUE
        }

        /**
         * Only refresh the data if null, otherwise the data will never be refreshed.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> IfNull(): ExpiresIn<T> = ifNull as ExpiresIn<T>

        /**
         * Refresh the data after given duration, provided that we know data's last loaded time.
         *
         * If the data was never loaded then provider is expected to return `0L`.
         *
         * Use `Long.MAX_VALUE` as duration if you don't want the data to be automatically
         * refreshed, except when the data was never loaded yet, in which case it will be loaded.
         *
         * All times are in milliseconds.
         */
        fun <T> Duration(duration: Long, loadedAt: T.() -> Long): ExpiresIn<T> {
            require(duration > 0L) { "Expiration time ($duration) should be > 0" }

            return object : ExpiresIn<T> {
                override fun expiresIn(data: T): Long {
                    val lastLoaded = loadedAt(data)
                    return when {
                        lastLoaded <= 0L -> 0L // Never loaded
                        duration == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
                        else -> lastLoaded + duration - now() // Calculating expiration time
                    }
                }
            }
        }

        /**
         * Refresh the data defined with [LoadedAt] interface after given duration.
         *
         * See [Duration].
         */
        fun <T : LoadedAt?> Duration(duration: Long): ExpiresIn<T> =
            Duration(duration) { this?.loadedAt ?: 0L }
    }
}


/**
 * Marks data that knows when it was loaded.
 *
 * Note: given a list of [LoadedAt] items we can calculate its overall loaded time as
 * `list.minOfOrNull { it.loadedAt } ?: 0`.
 */
interface LoadedAt {
    /**
     * Time in milliseconds when the data was last loaded.
     */
    val loadedAt: Long
}
