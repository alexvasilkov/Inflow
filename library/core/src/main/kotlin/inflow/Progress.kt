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
     * [InflowConfig.loader].
     *
     * Percentage can be calculated with [rate] function.
     */
    data class State internal constructor(val current: Float, val total: Float) : Progress() {
        /**
         * Percentage calculated as `current / total` (but never greater than `1`).
         * If `total <= 0` or `current < 0` then `0` will be returned.
         */
        fun rate(): Float = if (total > 0f && current >= 0f) min(current / total, 1f) else 0f
    }
}

/**
 * Intermediate loading progress tracker.
 */
interface ProgressTracker {
    fun state(current: Float, total: Float)
}

