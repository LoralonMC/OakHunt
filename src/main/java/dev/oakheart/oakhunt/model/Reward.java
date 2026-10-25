package dev.oakheart.oakhunt.model;

/**
 * One reward inside a milestone. {@code value} is the item id, key id or command, depending on
 * type; {@code item} holds a serialized vanilla item for {@link RewardType#ITEM}.
 */
public record Reward(int id, int milestoneId, RewardType type, String value, int amount, byte[] item) {
}
