package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Pays milestones. Items go into the inventory; whatever doesn't fit goes straight to OakOverflow
 * (or drops at the player's feet without it). A find never waits on inventory space.
 */
public class RewardService {

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final OakHunt plugin;

    public RewardService(OakHunt plugin) {
        this.plugin = plugin;
    }

    /** Pays every milestone the player has reached and not yet been paid in this run. */
    public void checkMilestones(Player player, Hunt hunt, Run run) {
        int found = plugin.getProgressService().found(run, player.getUniqueId(), hunt);
        int total = plugin.getHuntManager().liveTotal(hunt);
        for (Milestone milestone : plugin.getHuntManager().milestones(hunt)) {
            if (!milestone.isReached(found, total)) continue;
            if (plugin.getProgressService().hasPaid(run, player.getUniqueId(), milestone.getId())) continue;
            plugin.getProgressService().markPaid(run, player.getUniqueId(), milestone.getId());
            pay(player, hunt, milestone, found, total);
            plugin.getDatabase().audit(player.getName(), hunt.getId(), "payout",
                    "run " + run.getNumber() + " milestone " + label(milestone));
        }
    }

    /** Delivers a milestone's rewards and messages. */
    public void pay(Player player, Hunt hunt, Milestone milestone, int found, int total) {
        pay(player, hunt, milestone, found, total, false);
    }

    /**
     * @param test the GUI's unrecorded test payout: the broadcast goes to the tester only
     */
    public void pay(Player player, Hunt hunt, Milestone milestone, int found, int total, boolean test) {
        for (Reward reward : milestone.getRewards()) {
            deliver(player, hunt, reward);
        }
        TagResolver[] resolvers = resolvers(player, hunt, milestone, found, total);
        sendMessage(player, milestone, resolvers);
        Component broadcast = broadcast(milestone, resolvers);
        if (broadcast != null) {
            if (test) player.sendMessage(broadcast);
            else Bukkit.broadcast(broadcast);
        }
        plugin.getFeedbackService().milestone(player);
    }

    /**
     * Shows a milestone's message and broadcast to the viewer only, filled in as if they had just
     * reached it. Pays nothing.
     */
    public void preview(Player viewer, Hunt hunt, Milestone milestone) {
        int total = plugin.getHuntManager().liveTotal(hunt);
        int found = milestone.isAll() ? total : milestone.getThreshold();
        TagResolver[] resolvers = resolvers(viewer, hunt, milestone, found, total);
        plugin.getMessages().cmd(viewer, "preview-header", resolvers);
        sendMessage(viewer, milestone, resolvers);
        Component broadcast = broadcast(milestone, resolvers);
        if (broadcast != null) {
            plugin.getMessages().cmd(viewer, "preview-broadcast");
            viewer.sendMessage(broadcast);
        } else {
            plugin.getMessages().cmd(viewer, "preview-no-broadcast");
        }
        plugin.getFeedbackService().milestone(viewer);
    }

    private TagResolver[] resolvers(Player player, Hunt hunt, Milestone milestone, int found, int total) {
        return new TagResolver[]{Messages.hunt(hunt), Messages.text("player", player.getName()),
                Messages.text("found", found), Messages.text("total", total), Messages.text("milestone", label(milestone))};
    }

    private void sendMessage(Player player, Milestone milestone, TagResolver[] resolvers) {
        if (milestone.getMessage() != null && !milestone.getMessage().isBlank()) {
            player.sendMessage(plugin.getMessages().raw(milestone.getMessage(), resolvers));
        } else {
            plugin.getMessages().send(player, milestone.isAll() ? "milestone-complete" : "milestone-reached", resolvers);
        }
    }

    private Component broadcast(Milestone milestone, TagResolver[] resolvers) {
        if (milestone.getBroadcast() == null || milestone.getBroadcast().isBlank()) return null;
        return plugin.getMessages().raw(milestone.getBroadcast(), resolvers);
    }

    public void deliver(Player player, Hunt hunt, Reward reward) {
        switch (reward.type()) {
            case EXECUTABLE_ITEM -> give(player, hunt, reward,
                    plugin.getExecutableItemsHook().build(reward.value(), reward.amount(), player));
            case NEXO_ITEM -> give(player, hunt, reward, plugin.getNexoHook().build(reward.value(), reward.amount()));
            case ITEM -> give(player, hunt, reward, reward.item() == null ? null : ItemStack.deserializeBytes(reward.item()));
            case CRATE_KEY -> dispatch("phoenixcrates giveKey " + reward.value() + " " + player.getName() + " " + reward.amount());
            case COMMAND -> dispatch(reward.value().replace("{player}", player.getName()));
        }
    }

    private void give(Player player, Hunt hunt, Reward reward, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            plugin.getLogger().severe("Hunt " + hunt.getId() + " reward " + reward.type() + " '" + reward.value()
                    + "' could not be built, so " + player.getName() + " did not receive it");
            plugin.getDatabase().audit(player.getName(), hunt.getId(), "reward-failed", reward.type() + " " + reward.value());
            plugin.getMessages().send(player, "reward-failed");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            if (plugin.getOverflowHook().store(player, rest)) {
                plugin.getMessages().send(player, "reward-overflow");
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
                plugin.getMessages().send(player, "reward-dropped");
            }
        }
    }

    private void dispatch(String command) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reward command failed: " + command, e);
        }
    }

    /** Crate key ids from the PhoenixCrates keys folder (file names without .yml). */
    public List<String> crateKeys() {
        File folder = new File(plugin.getDataFolder().getParentFile(), plugin.getConfigManager().getKeysFolder());
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> keys = new ArrayList<>();
        if (files != null) {
            for (File file : files) keys.add(file.getName().substring(0, file.getName().length() - 4));
        }
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        return keys;
    }

    public boolean isValidKeyId(String id) {
        return id != null && KEY_ID.matcher(id).matches();
    }

    public String label(Milestone milestone) {
        return milestone.isAll() ? "all" : String.valueOf(milestone.getThreshold());
    }

    public String describe(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> {
                ItemStack item = reward.item() == null ? null : ItemStack.deserializeBytes(reward.item());
                yield item == null ? "item" : item.getAmount() + "x " + item.getType().getKey().getKey().toLowerCase(Locale.ROOT);
            }
            case CRATE_KEY, EXECUTABLE_ITEM, NEXO_ITEM -> reward.amount() + "x " + reward.value();
            case COMMAND -> reward.value();
        };
    }
}
