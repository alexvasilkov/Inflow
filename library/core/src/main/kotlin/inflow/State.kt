package inflow

import inflow.State.Idle
import inflow.State.Loading
import kotlin.math.min

/**
 * Represents loading state, can be [Idle] or [Loading]. Both [Idle] and [Loading] states have their
 * own sub-states, e.g. error is represented by [Idle.Error].
 */
public sealed class State {

    public sealed class Idle : State() {
        /**
         * Initial state, data loading never started yet.
         */
        public object Initial : Idle()

        /**
         * Last loading was successful.
         */
        public object Success : Idle()

        /**
         * Last loading failed.
         */
        public class Error internal constructor(
            @JvmField
            public val throwable: Throwable,
            @JvmField
            internal val id: Int,
            @JvmField
            internal val markHandled: (Error) -> Boolean
        ) : Idle()
    }

    public sealed class Loading : State() {
        /**
         * Loading is started.
         */
        public object Started : Loading()

        /**
         * Loading progress ([current] / [total]) as tracked by [LoadTracker] instance passed to
         * the loader (see [InflowConfig.data]).
         *
         * Percentage can be calculated with [state] function.
         */
        public class Progress internal constructor(
            @JvmField
            public val current: Double,
            @JvmField
            public val total: Double
        ) : Loading() {
            /**
             * Percentage calculated as `current / total` (but never greater than `1`).
             * If `total <= 0` or `current < 0` then `0` will be returned.
             */
            public fun state(): Double =
                if (total > 0.0 && current >= 0.0) min(current / total, 1.0) else 0.0
        }
    }

}
