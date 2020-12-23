package inflow

import inflow.Progress.Active
import inflow.Progress.Idle
import inflow.Progress.State
import kotlin.math.min

/**
 * Represents loading state, can be [Idle], [Active] or [State] (optionally).
 */
sealed class Progress {
    /**
     * Not loading.
     */
    object Idle : Progress()

    /**
     * Loading is started.
     */
    object Active : Progress()

    /**
     * Loading progress ([current] / [total]) as tracked by [ProgressTracker] instance passed to
     * the loader (see [InflowConfig.data]).
     *
     * Percentage can be calculated with [rate] function.
     */
    data class State internal constructor(val current: Double, val total: Double) : Progress() {
        /**
         * Percentage calculated as `current / total` (but never greater than `1`).
         * If `total <= 0` or `current < 0` then `0` will be returned.
         */
        fun rate(): Double = if (total > 0.0 && current >= 0.0) min(current / total, 1.0) else 0.0
    }
}

/**
 * Intermediate loading progress tracker.
 */
interface ProgressTracker {
    fun state(current: Double, total: Double)
}
