package dev.oakheart.oakhunt.model;

/**
 * When a hunt's clues unlock for a player.
 */
public enum ClueMode {
    /** Every clue is visible from the start. */
    ALL,
    /** One more clue every N finds. */
    EVERY_N,
    /** One more clue per milestone reached. */
    MILESTONE;

    public static ClueMode parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
