package inflow.paging

/** Represents a part of paged list from the beginning to an intermediate point. */
public interface Paged<T> {
    /** Paged items list. */
    public val items: List<T>

    /** Whether we can load next page to get more items (from local cache or remote source). */
    public val hasNext: Boolean
}
