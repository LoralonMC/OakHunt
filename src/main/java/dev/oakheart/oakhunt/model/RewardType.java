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
    COMMAND,
    /** OakTags chat tag, granted straight to the player. */
    TAG,
    /** OakTags chat tag voucher, built fresh from the tag id at payout. */
    TAG_VOUCHER
}
