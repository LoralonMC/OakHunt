package dev.oakheart.oakhunt.model;

public enum RewardType {
    /** ExecutableItems item, built fresh from its id at payout. */
    EXECUTABLE_ITEM,
    /** Nexo item, built fresh from its id at payout. */
    NEXO_ITEM,
    /** PhoenixCrates key, given through its giveKey command. */
    CRATE_KEY,
    /** A vanilla item stored as a copy. */
    ITEM,
    /** A console command; {player} is the player's name. */
    COMMAND
}
