@file:Suppress("FunctionName") // Function names are pretending to be interface constructors

package inflow

import inflow.utils.now

/**
 * Expiration time provider to control when the data should be automatically refreshed (or
 * invalidated). See [expiresIn] method.
 *
 * Default implementations: [ExpiresNever], [ExpiresIfNull], [ExpiresAt], [ExpiresIn], [ExpiresIf].
 */
interface ExpirationProvider<T> {
    /**
     * Timeout in milliseconds after which given data will expire and should be automatically
     * refreshed (or invalidated).
     * In other words, how many milliseconds (from now) the data can be considered fresh (or valid).
     *
     * Return `0L` if the data should be refreshed (or invalidated) immediately.
     *
     * Return `Long.MAX_VALUE` if the data should never be refreshed (or invalidated) automatically.
     *
     * Note that this method will be called again right after expiration timeout and if it still
     * returns a positive timeout then it will start to wait again, until this method will finally
     * return 0 or negative value. This can be useful to have a timely validation check which is not
     * time-based itself, see [ExpiresIf]
     */
    fun expiresIn(data: T): Long
}


private val never = object : ExpirationProvider<Any?> {
    override fun expiresIn(data: Any?) = Long.MAX_VALUE
}

private val ifNull = object : ExpirationProvider<Any?> {
    override fun expiresIn(data: Any?) = if (data == null) 0L else Long.MAX_VALUE
}

/**
 * The data will never be automatically refreshed.
 */
@Suppress("UNCHECKED_CAST") // Pretending to be a class
fun <T> ExpiresNever(): ExpirationProvider<T> = never as ExpirationProvider<T>

/**
 * Only refresh the data if it's `null`, otherwise it will never be refreshed.
 */
@Suppress("UNCHECKED_CAST") // Pretending to be a class
fun <T> ExpiresIfNull(): ExpirationProvider<T> = ifNull as ExpirationProvider<T>

/**
 * Refreshes (or invalidates) the data at specific time as defined by [expiresAt].
 *
 * If the data was never loaded then [expiresAt] provider is expected to return `0L`. If the data
 * should never expire (or be invalidated) then return `Long.MAX_VALUE` from [expiresAt] provider.
 *
 * The time should be in milliseconds since unix epoch.
 */
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
 * The time from [loadedAt] should be in milliseconds since unix epoch.
 * Duration time is also in milliseconds.
 */
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

/**
 * Checks if the data is expired (or invalid) using [isExpired] lambda at a regular [interval].
 *
 * It can be useful in cases when data expiration (or validity) is not time-based, so that we can't
 * say for sure when exactly it will expire. Instead we'll just start to check it periodically.
 *
 * For example [ExpiresIfNull] can be implemented as `ExpiresIf(Long.MAX_VALUE) { it == null }`,
 * meaning that we'll check for nullability once but won't ever need to check it again.
 */
fun <T> ExpiresIf(interval: Long, isExpired: (T) -> Boolean): ExpirationProvider<T> {
    require(interval > 0L) { "Expiration check interval ($interval) should be > 0" }

    return object : ExpirationProvider<T> {
        override fun expiresIn(data: T): Long {
            return if (isExpired(data)) 0L else interval
        }
    }
}
