package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * One milestone's rewards, message and broadcast. Shift-click a reward to remove it. Setting the
 * message or broadcast previews it to the editor straight away.
 */
public class MilestoneMenu extends Menu {

    private final Hunt hunt;
    private final Milestone milestone;

    public MilestoneMenu(OakHunt plugin, Player viewer, Hunt hunt, Milestone milestone) {
        super(plugin, viewer, 4, "gui.milestone-title", Messages.text("milestone", plugin.getRewardService().label(milestone)));
        this.hunt = hunt;
        this.milestone = milestone;
    }

    @Override
    protected void draw() {
        List<Reward> rewards = List.copyOf(milestone.getRewards());
        for (int i = 0; i < Math.min(18, rewards.size()); i++) {
            Reward reward = rewards.get(i);
            set(i, ItemBuilder.of(icon(reward))
                    .name(plugin.getMessages().line("gui.reward-icon-name",
                            Messages.text("type", reward.type().name().toLowerCase(Locale.ROOT).replace('_', ' '))))
                    .lore(plugin.getMessages().lines("gui.reward-icon-lore",
                            Messages.text("reward", plugin.getRewardService().describe(reward))))
                    .build(), e -> {
                if (!e.isShiftClick()) return;
                if (reward.type() == RewardType.COMMAND && !viewer.hasPermission("oakhunt.rewards.commands")) {
                    plugin.getMessages().cmd(viewer, "no-permission");
                    return;
                }
                plugin.getHuntManager().removeReward(milestone, reward, viewer.getName());
                redraw();
            });
        }
        set(20, button(Material.EMERALD, "add-reward"), e -> plugin.getMenus().openRewardType(viewer, hunt, milestone));
        set(22, button(Material.OAK_SIGN, "milestone-message",
                Messages.text("message", milestone.getMessage() == null ? "default" : milestone.getMessage())), e ->
                plugin.getChatPrompt().ask(viewer, "prompt-milestone-message", input -> {
                    if (input != null) {
                        milestone.setMessage(input.equalsIgnoreCase("default") ? null : input);
                        plugin.getHuntManager().saveMilestone(milestone, viewer.getName());
                        plugin.getRewardService().preview(viewer, hunt, milestone);
                    }
                    plugin.getMenus().openMilestone(viewer, hunt, milestone);
                }));
        boolean broadcasting = milestone.getBroadcast() != null;
        set(24, button(broadcasting ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                broadcasting ? "broadcast-on" : "broadcast-off"), e -> {
            if (broadcasting && !e.isRightClick()) {
                milestone.setBroadcast(null);
                plugin.getHuntManager().saveMilestone(milestone, viewer.getName());
                redraw();
                return;
            }
            plugin.getChatPrompt().ask(viewer, "prompt-broadcast", input -> {
                if (input != null) {
                    milestone.setBroadcast(input.equalsIgnoreCase("default")
                            ? plugin.getMessages().manager().getMessageText("default-broadcast") : input);
                    plugin.getHuntManager().saveMilestone(milestone, viewer.getName());
                    plugin.getRewardService().preview(viewer, hunt, milestone);
                }
                plugin.getMenus().openMilestone(viewer, hunt, milestone);
            });
        });
        set(30, button(Material.SPYGLASS, "preview-messages"), e -> plugin.getRewardService().preview(viewer, hunt, milestone));
        set(32, button(Material.EXPERIENCE_BOTTLE, "test-pay"), e -> {
            if (!viewer.hasPermission("oakhunt.rewards.pay")) {
                plugin.getMessages().cmd(viewer, "test-pay-owner-only");
                return;
            }
            int total = plugin.getHuntManager().liveTotal(hunt);
            plugin.getRewardService().pay(viewer, hunt, milestone, milestone.isAll() ? total : milestone.getThreshold(), total, true);
            plugin.getMessages().cmd(viewer, "test-paid", Messages.text("milestone", plugin.getRewardService().label(milestone)));
        });
        back(27, () -> plugin.getMenus().openRewards(viewer, hunt));
    }

    private static ItemStack icon(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> reward.item() == null ? new ItemStack(Material.PAPER) : ItemStack.deserializeBytes(reward.item());
            case EXECUTABLE_ITEM -> new ItemStack(Material.ENCHANTED_BOOK);
            case NEXO_ITEM -> new ItemStack(Material.AMETHYST_SHARD);
            case CRATE_KEY -> new ItemStack(Material.TRIPWIRE_HOOK);
            case COMMAND -> new ItemStack(Material.COMMAND_BLOCK);
        };
    }
}
