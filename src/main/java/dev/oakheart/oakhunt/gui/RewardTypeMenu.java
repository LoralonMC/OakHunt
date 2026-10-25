package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Picks a reward type, then asks for its details.
 */
public class RewardTypeMenu extends Menu {

    private final Hunt hunt;
    private final Milestone milestone;

    public RewardTypeMenu(OakHunt plugin, Player viewer, Hunt hunt, Milestone milestone) {
        super(plugin, viewer, 3, "gui.reward-type-title");
        this.hunt = hunt;
        this.milestone = milestone;
    }

    @Override
    protected void draw() {
        set(10, button(Material.ENCHANTED_BOOK, "type-executable"), e -> askItem(RewardType.EXECUTABLE_ITEM));
        set(11, button(Material.AMETHYST_SHARD, "type-nexo"), e -> askItem(RewardType.NEXO_ITEM));
        set(12, button(Material.TRIPWIRE_HOOK, "type-key"), e -> plugin.getMenus().openKeyPicker(viewer, hunt, milestone));
        set(14, button(Material.CHEST, "type-held"), e -> addHeld());
        set(16, button(Material.COMMAND_BLOCK, "type-command"), e -> {
            if (!viewer.hasPermission("oakhunt.rewards.commands")) {
                plugin.getMessages().cmd(viewer, "command-rewards-owner-only");
                return;
            }
            plugin.getChatPrompt().ask(viewer, "prompt-command", input -> {
                if (input != null) {
                    String command = input.startsWith("/") ? input.substring(1) : input;
                    plugin.getHuntManager().addReward(milestone, RewardType.COMMAND, command, 1, null, viewer.getName());
                    plugin.getMessages().cmd(viewer, "reward-added");
                }
                plugin.getMenus().openMilestone(viewer, hunt, milestone);
            });
        });
        back(18, () -> plugin.getMenus().openMilestone(viewer, hunt, milestone));
    }

    /** ExecutableItems / Nexo: "<id> [amount]" in chat, checked by building it once. */
    private void askItem(RewardType type) {
        boolean ei = type == RewardType.EXECUTABLE_ITEM;
        if (ei ? !plugin.getExecutableItemsHook().isAvailable() : !plugin.getNexoHook().isAvailable()) {
            plugin.getMessages().cmd(viewer, "integration-missing", Messages.text("plugin", ei ? "ExecutableItems" : "Nexo"));
            return;
        }
        plugin.getChatPrompt().ask(viewer, "prompt-item-id", input -> {
            if (input != null) {
                String[] parts = input.trim().split("\\s+");
                int amount = 1;
                if (parts.length > 1) {
                    try {
                        amount = Math.max(1, Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                        // keep 1
                    }
                }
                ItemStack test = ei ? plugin.getExecutableItemsHook().build(parts[0], 1, viewer)
                        : plugin.getNexoHook().build(parts[0], 1);
                if (test == null) {
                    plugin.getMessages().cmd(viewer, "unknown-item-id", Messages.text("input", parts[0]));
                } else {
                    plugin.getHuntManager().addReward(milestone, type, parts[0], amount, null, viewer.getName());
                    plugin.getMessages().cmd(viewer, "reward-added");
                }
            }
            plugin.getMenus().openMilestone(viewer, hunt, milestone);
        });
    }

    /** Held vanilla item. EI and Nexo items are refused: a stored copy goes stale. */
    private void addHeld() {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            plugin.getMessages().cmd(viewer, "hold-an-item");
            return;
        }
        if (held.hasItemMeta()) {
            for (NamespacedKey key : held.getItemMeta().getPersistentDataContainer().getKeys()) {
                String namespace = key.getNamespace();
                if (namespace.equals("executableitems") || namespace.equals("nexo") || namespace.equals("score")) {
                    plugin.getMessages().cmd(viewer, "held-item-is-custom");
                    return;
                }
            }
        }
        plugin.getHuntManager().addReward(milestone, RewardType.ITEM, null, held.getAmount(), held.serializeAsBytes(), viewer.getName());
        plugin.getMessages().cmd(viewer, "reward-added");
        plugin.getMenus().openMilestone(viewer, hunt, milestone);
    }
}
