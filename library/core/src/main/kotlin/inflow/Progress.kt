package inflow

import inflow.Progress.Active
import inflow.Progress.Idle
import inflow.Progress.State
import kotlin.math.min

/**
 * Represents loading state, can be [Idle], [Active] or [State] (optionally).
 */
public sealed class Progress {
    /**
     * Not loading.
     */
    public object Idle : Progress()

    /**
     * Loading is started.
     */
    public object Active : Progress()

    /**
     * Loading progress ([current] / [total]) as tracked by [LoadTracker] instance passed to
     * the loader (see [InflowConfig.data]).
     *
     * Percentage can be calculated with [rate] function.
     */
    public class State internal constructor(
        @JvmField
        public val current: Double,
        @JvmField
        public val total: Double
    ) : Progress() {
        /**
         * Percentage calculated as `current / total` (but never greater than `1`).
         * If `total <= 0` or `current < 0` then `0` will be returned.
         */
        public fun rate(): Double =
            if (total > 0.0 && current >= 0.0) min(current / total, 1.0) else 0.0
    }
}

/**
 * Intermediate loading progress tracker.
 */
public interface LoadTracker {
    /**
     * Allows tracking intermediate loading state that can be collected with [Inflow.progress] as
     * [Progress.State].
     */
    public fun state(current: Double, total: Double)
}
