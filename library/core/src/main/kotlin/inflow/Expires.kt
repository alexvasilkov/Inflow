package inflow

import inflow.utils.now

/**
 * Expiration time provider to control when the data should be automatically refreshed (or
 * invalidated). See [expiresIn] method.
 *
 * Default implementations: [Expires.never], [Expires.ifNull], [Expires.at], [Expires.after].
 */
public fun interface Expires<T> {
    /**
     * Timeout in milliseconds after which given data will expire and should be automatically
     * refreshed (or invalidated).
     * In other words, how many milliseconds (from now) the data can be considered fresh (or valid).
     *
     * Return `0L` if the data should be refreshed (or invalidated) immediately.
     *
     * Return `Long.MAX_VALUE` if the data should never be refreshed (or invalidated) automatically.
     */
    public fun expiresIn(data: T): Long // TODO: Make suspend?


    public companion object {
        private val never = Expires<Any?> { Long.MAX_VALUE }
        private val ifNull = Expires<Any?> { data -> if (data == null) 0L else Long.MAX_VALUE }

        /**
         * The data will never be automatically refreshed.
         */
        @Suppress("UNCHECKED_CAST")
        public fun <T> never(): Expires<T> = never as Expires<T>

        /**
         * Only refresh the data if it's `null`, otherwise it will never be refreshed.
         */
        @Suppress("UNCHECKED_CAST")
        public fun <T> ifNull(): Expires<T> = ifNull as Expires<T>

        /**
         * Refreshes (or invalidates) the data at specific time as defined by [expiresAt].
         *
         * If the data was never loaded then [expiresAt] provider is expected to return `0L`.
         * If the data should never expire (or be invalidated) then return `Long.MAX_VALUE` from
         * [expiresAt] provider.
         *
         * The time should be in milliseconds since unix epoch.
         */
        public fun <T> at(expiresAt: (T) -> Long): Expires<T> = Expires { data ->
            val expiresAtTime = expiresAt(data)
            when {
                expiresAtTime <= 0L -> 0L // Never loaded
                expiresAtTime == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
                else -> expiresAtTime - now() // Calculating expiration time
            }
        }


        /**
         * Refreshes (or invalidates) the data after given [duration], provided that we know data's
         * last loaded time.
         *
         * If [loadedAt] provider returns 0 or negative value then refresh will be triggered
         * immediately. Otherwise `Long.MAX_VALUE` can be used as duration or as the result of
         * [loadedAt] provider to never trigger data refresh.
         *
         * The time returned by [loadedAt] should be in milliseconds since unix epoch.
         * Duration time is also in milliseconds.
         */
        public fun <T> after(duration: Long, loadedAt: (T) -> Long): Expires<T> {
            require(duration > 0L) { "Expiration duration ($duration) should be > 0" }

            return Expires { data ->
                val lastLoaded = loadedAt(data)
                when {
                    lastLoaded <= 0L -> 0L // Never loaded
                    lastLoaded == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
                    duration == Long.MAX_VALUE -> Long.MAX_VALUE // Never expires
                    else -> lastLoaded + duration - now() // Calculating expiration time
                }
            }
        }
    }

}
