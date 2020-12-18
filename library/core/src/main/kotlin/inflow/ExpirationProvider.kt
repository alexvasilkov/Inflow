package inflow

import inflow.utils.now

/**
 * Expiration time provider to control when the data should be automatically refreshed (or
 * invalidated).
 *
 * Default implementations: [ExpiresIfNull], [ExpiresAt], [ExpiresIn].
 */
interface ExpirationProvider<T> {
    /**
     * Relative time in milliseconds after which given data will expire and should be automatically
     * refreshed (or invalidated).
     * In other words, how many milliseconds (from now) the data can be considered fresh (or valid).
     *
     * Return `0L` if the data should be refreshed (or invalidated) immediately.
     *
     * Return `Long.MAX_VALUE` if the data should never be refreshed (or invalidated) automatically.
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
 * Refreshes (or invalidates) the data at specific time as defined by [expiresAt].
 *
 * If the data was never loaded then [expiresAt] provider is expected to return `0L`. If the data
 * should never expire (or be invalidated) then return `Long.MAX_VALUE` from [expiresAt] provider.
 *
 * All times are in milliseconds.
 */
@Suppress("FunctionName") // Pretending to be a class
fun <T> ExpiresAt(expiresAt: (T) -> Long): ExpirationProvider<T> {
    return object : ExpirationProvider<T> {
        override fun expiresIn(data: T): Long {
            val expiresAtTime = expiresAt(data)
            return when {
                expiresAtTime <= 0L -> 0L // Never loaded
                expiresAtTime == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
                else -> expiresAtTime - now() // Calculating expiration time
            }
        }
    }
}

/**
 * Refreshes (or invalidates) the data after given [duration], provided that we know data's last
 * loaded time.
 *
 * If the data was never loaded then [loadedAt] provider is expected to return `0L`.
 *
 * Use `Long.MAX_VALUE` as duration if you don't want the data to be automatically refreshed
 * (or invalidated).
 *
 * All times are in milliseconds.
 */
@Suppress("FunctionName") // Pretending to be a class
fun <T> ExpiresIn(duration: Long, loadedAt: (T) -> Long): ExpirationProvider<T> {
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
