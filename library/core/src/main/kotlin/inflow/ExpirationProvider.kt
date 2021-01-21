@file:Suppress("FunctionName") // Function names are pretending to be interface constructors

package inflow

import inflow.utils.now

/**
 * Expiration time provider to control when the data should be automatically refreshed (or
 * invalidated). See [expiresIn] method.
 *
 * Default implementations: [ExpiresNever], [ExpiresIfNull], [ExpiresAt], [ExpiresIn].
 */
public fun interface ExpirationProvider<T> {
    /**
     * Timeout in milliseconds after which given data will expire and should be automatically
     * refreshed (or invalidated).
     * In other words, how many milliseconds (from now) the data can be considered fresh (or valid).
     *
     * Return `0L` if the data should be refreshed (or invalidated) immediately.
     *
     * Return `Long.MAX_VALUE` if the data should never be refreshed (or invalidated) automatically.
     */
    public fun expiresIn(data: T): Long
}


private val never = ExpirationProvider<Any?> { Long.MAX_VALUE }

private val ifNull = ExpirationProvider<Any?> { data -> if (data == null) 0L else Long.MAX_VALUE }

/**
 * The data will never be automatically refreshed.
 */
@Suppress("UNCHECKED_CAST") // Pretending to be a class
public fun <T> ExpiresNever(): ExpirationProvider<T> = never as ExpirationProvider<T>

/**
 * Only refresh the data if it's `null`, otherwise it will never be refreshed.
 */
@Suppress("UNCHECKED_CAST") // Pretending to be a class
public fun <T> ExpiresIfNull(): ExpirationProvider<T> = ifNull as ExpirationProvider<T>

/**
 * Refreshes (or invalidates) the data at specific time as defined by [expiresAt].
 *
 * If the data was never loaded then [expiresAt] provider is expected to return `0L`. If the data
 * should never expire (or be invalidated) then return `Long.MAX_VALUE` from [expiresAt] provider.
 *
 * The time should be in milliseconds since unix epoch.
 */
public fun <T> ExpiresAt(expiresAt: (T) -> Long): ExpirationProvider<T> {
    return ExpirationProvider { data ->
        val expiresAtTime = expiresAt(data)
        when {
            expiresAtTime <= 0L -> 0L // Never loaded
            expiresAtTime == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
            else -> expiresAtTime - now() // Calculating expiration time
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
 * (or invalidated). Unless [loadedAt] returns 0 (or negative) value which will still trigger the
 * loading.
 *
 * The time returned by [loadedAt] should be in milliseconds since unix epoch.
 * Duration time is also in milliseconds.
 */
public fun <T> ExpiresIn(duration: Long, loadedAt: (T) -> Long): ExpirationProvider<T> {
    require(duration > 0L) { "Expiration duration ($duration) should be > 0" }

    return ExpirationProvider { data ->
        val lastLoaded = loadedAt(data)
        when {
            lastLoaded <= 0L -> 0L // Never loaded
            duration == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
            else -> lastLoaded + duration - now() // Calculating expiration time
        }
    }
}
