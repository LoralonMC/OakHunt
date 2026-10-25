package dev.oakheart.oakhunt.model;

/**
 * Where a hunt is in its lifecycle. Derived from the hunt's stored fields, never stored itself.
 */
public enum HuntState {
    /** Never run and not scheduled. */
    DRAFT,
    /** Not running, with a start time set. */
    SCHEDULED,
    /** Running: heads are real blocks in the world. */
    ACTIVE,
    /** Has run before and is not running now. */
    ENDED;

    public String key() {
        return name().toLowerCase();
    }
}
