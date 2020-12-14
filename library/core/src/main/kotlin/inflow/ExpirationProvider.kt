package inflow

import inflow.utils.now

/**
 * Expiration time provider to control when the data should be automatically refreshed.
 *
 * Default providers are available with: [ExpiresIfNull] and [ExpiresIn].
 */
interface ExpirationProvider<T> {
    /**
     * Relative time in milliseconds after which given data will expire and should be automatically
     * refreshed. In other words, how many milliseconds (from now) the data can be considered fresh.
     *
     * Return `0L` if the data should be refreshed immediately.
     *
     * Return `Long.MAX_VALUE` if the data should never be refreshed automatically.
     */
    fun expiresIn(data: T): Long

}

private val ifNull = object : ExpirationProvider<Any?> {
    override fun expiresIn(data: Any?) = if (data == null) 0L else Long.MAX_VALUE
}

/**
 * Only refresh the data if it's `null`, otherwise it will never be refreshed.
 */
@Suppress("UNCHECKED_CAST", "FunctionName") // Pretending to be a class
fun <T> ExpiresIfNull(): ExpirationProvider<T> = ifNull as ExpirationProvider<T>

/**
 * Refresh the data after given duration, provided that we know data's last loaded time.
 *
 * If the data was never loaded then provider is expected to return `0L`.
 *
 * Use `Long.MAX_VALUE` as duration if you don't want the data to be automatically refreshed.
 *
 * All times are in milliseconds.
 */
@Suppress("FunctionName") // Pretending to be a class
fun <T> ExpiresIn(duration: Long, loadedAt: T.() -> Long): ExpirationProvider<T> {
    require(duration > 0L) { "Expiration duration ($duration) should be > 0" }

    return object : ExpirationProvider<T> {
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
 * Use `Long.MAX_VALUE` as duration if you don't want the data to be automatically refreshed
 * (unless the data is `null`).
 */
@Suppress("FunctionName") // Pretending to be a class
fun <T : LoadedAt?> ExpiresIn(duration: Long): ExpirationProvider<T> {
    return ExpiresIn(duration, loadedAt = { this?.loadedAt ?: 0L })
}


/**
 * Marks data that knows when it was loaded.
 *
 * If the data was never loaded then it is expected to return `0L`.
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
