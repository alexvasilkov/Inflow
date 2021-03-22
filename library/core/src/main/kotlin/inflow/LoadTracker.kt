package inflow

import inflow.State.Loading

/**
 * Intermediate loading progress tracker.
 */
public interface LoadTracker { // TODO: Rename to StateHandle and provide a way to signal an error?
    /**
     * Allows tracking intermediate loading progress that can be collected with
     * [Inflow.refreshState] as [Loading.Progress].
     */
    public fun progress(current: Double, total: Double)
}
