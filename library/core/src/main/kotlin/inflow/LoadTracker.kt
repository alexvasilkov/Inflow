package inflow

import inflow.State.Loading

/**
 * Intermediate loading progress tracker.
 */
public interface LoadTracker {
    /**
     * Allows tracking intermediate loading progress that can be collected with [Inflow.state] as
     * [Loading.Progress].
     */
    public fun progress(current: Double, total: Double)
}
