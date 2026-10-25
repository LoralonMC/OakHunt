package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A hunt's milestones. Click to edit, shift-left to test-pay to yourself (not recorded),
 * shift-right to remove.
 */
public class RewardsMenu extends Menu {

    private final Hunt hunt;

    public RewardsMenu(OakHunt plugin, Player viewer, Hunt hunt) {
        super(plugin, viewer, 4, "gui.rewards-title", Messages.text("hunt_id", hunt.getId()));
        this.hunt = hunt;
    }

    @Override
    protected void draw() {
        List<Milestone> milestones = plugin.getHuntManager().milestones(hunt);
        for (int i = 0; i < Math.min(27, milestones.size()); i++) {
            Milestone milestone = milestones.get(i);
            set(i, icon(plugin, milestone), e -> {
                if (e.isShiftClick() && e.isLeftClick()) {
                    int total = plugin.getHuntManager().liveTotal(hunt);
                    plugin.getRewardService().pay(viewer, hunt, milestone,
                            milestone.isAll() ? total : milestone.getThreshold(), total);
                    plugin.getMessages().cmd(viewer, "test-paid", Messages.text("milestone", plugin.getRewardService().label(milestone)));
                } else if (e.isShiftClick()) {
                    plugin.getMenus().choose(viewer, "confirm-remove-milestone", () -> {
                        plugin.getHuntManager().removeMilestone(milestone, viewer.getName());
                        plugin.getMenus().openRewards(viewer, hunt);
                    }, () -> plugin.getMenus().openRewards(viewer, hunt));
                } else {
                    plugin.getMenus().openMilestone(viewer, hunt, milestone);
                }
            });
        }
        back(27, () -> plugin.getMenus().openHunt(viewer, hunt));
        set(31, button(Material.EMERALD, "add-milestone"), e -> plugin.getChatPrompt().ask(viewer, "prompt-milestone", input -> {
            if (input != null) {
                int threshold;
                if (input.trim().toLowerCase(Locale.ROOT).equals("all")) {
                    threshold = Milestone.ALL;
                } else {
                    try {
                        threshold = Integer.parseInt(input.trim());
                    } catch (NumberFormatException ex) {
                        threshold = 0;
                    }
                }
                if (threshold == 0 || threshold < Milestone.ALL) {
                    plugin.getMessages().cmd(viewer, "invalid-milestone", Messages.text("input", input));
                } else if (plugin.getHuntManager().addMilestone(hunt, threshold, viewer.getName()).isEmpty()) {
                    plugin.getMessages().cmd(viewer, "milestone-exists", Messages.text("input", input));
                }
            }
            plugin.getMenus().openRewards(viewer, hunt);
        }));
    }

    static org.bukkit.inventory.ItemStack icon(OakHunt plugin, Milestone milestone) {
        List<Component> lore = new ArrayList<>();
        for (Reward reward : milestone.getRewards()) {
            lore.add(plugin.getMessages().line("gui.reward-line",
                    Messages.text("type", reward.type().name().toLowerCase(Locale.ROOT).replace('_', ' ')),
                    Messages.text("reward", plugin.getRewardService().describe(reward))));
        }
        if (milestone.getRewards().isEmpty()) lore.add(plugin.getMessages().line("gui.no-rewards"));
        lore.addAll(plugin.getMessages().lines("gui.milestone-icon-lore",
                Messages.text("broadcast", milestone.getBroadcast() == null ? "off" : "on")));
        String nameKey = milestone.isAll() ? "gui.milestone-all-name" : "gui.milestone-name";
        return ItemBuilder.of(milestone.isAll() ? Material.NETHER_STAR : Material.EXPERIENCE_BOTTLE)
                .name(plugin.getMessages().line(nameKey, Messages.text("count", milestone.getThreshold())))
                .lore(lore)
                .build();
    }
}
