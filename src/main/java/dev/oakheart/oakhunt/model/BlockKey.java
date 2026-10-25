package dev.oakheart.oakhunt.model;

import org.bukkit.block.Block;

/**
 * A block position in a named world, usable as a map key.
 */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Head head) {
        return new BlockKey(head.getWorld(), head.getX(), head.getY(), head.getZ());
    }
}
