package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Lists PhoenixCrates keys from its keys folder; click one, then type the amount.
 */
public class KeyPickerMenu extends Menu {

    private final Hunt hunt;
    private final Milestone milestone;

    public KeyPickerMenu(OakHunt plugin, Player viewer, Hunt hunt, Milestone milestone) {
        super(plugin, viewer, 3, "gui.keys-title");
        this.hunt = hunt;
        this.milestone = milestone;
    }

    @Override
    protected void draw() {
        List<String> keys = plugin.getRewardService().crateKeys();
        if (keys.isEmpty()) set(13, button(Material.BARRIER, "no-keys"));
        for (int i = 0; i < Math.min(18, keys.size()); i++) {
            String key = keys.get(i);
            set(i, ItemBuilder.of(Material.TRIPWIRE_HOOK)
                    .name(plugin.getMessages().line("gui.key-icon-name", Messages.text("key", key)))
                    .lore(plugin.getMessages().lines("gui.key-icon-lore"))
                    .build(), e -> plugin.getChatPrompt().ask(viewer, "prompt-amount", input -> {
                if (input != null) {
                    int amount;
                    try {
                        amount = Integer.parseInt(input.trim());
                    } catch (NumberFormatException ex) {
                        amount = 0;
                    }
                    if (amount < 1) {
                        plugin.getMessages().cmd(viewer, "invalid-amount", Messages.text("input", input));
                    } else {
                        plugin.getHuntManager().addReward(milestone, RewardType.CRATE_KEY, key, amount, null, viewer.getName());
                        plugin.getMessages().cmd(viewer, "reward-added");
                    }
                }
                plugin.getMenus().openMilestone(viewer, hunt, milestone);
            }));
        }
        back(18, () -> plugin.getMenus().openRewardType(viewer, hunt, milestone));
    }
}
