package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.ClueMode;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.HuntSetting;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The player journal: progress in each active hunt, the next milestone, unlocked clues, and a link
 * to past hunts. Never shows a location.
 */
public class JournalMenu extends Menu {

    private static final int[] CLUE_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Hunt hunt;

    /**
     * @param hunt the hunt to show, or null to pick (straight to the page when only one is active)
     */
    public JournalMenu(OakHunt plugin, Player viewer, Hunt hunt) {
        super(plugin, viewer, 6, "gui.journal-title");
        List<Hunt> active = plugin.getHuntManager().activeHunts();
        this.hunt = hunt != null ? hunt : (active.size() == 1 ? active.getFirst() : null);
    }

    @Override
    protected void draw() {
        List<Hunt> active = plugin.getHuntManager().activeHunts();
        if (hunt == null) {
            if (active.isEmpty()) set(22, button(Material.BOOK, "journal-none"));
            for (int i = 0; i < Math.min(HuntListMenu.SLOTS.length, active.size()); i++) {
                Hunt each = active.get(i);
                set(HuntListMenu.SLOTS[i], ItemBuilder.of(HuntListMenu.icon(plugin, each))
                        .lore(plugin.getMessages().lines("gui.journal-pick-lore", progress(each)))
                        .build(), e -> plugin.getMenus().openJournal(viewer, each));
            }
        } else {
            drawHunt();
            if (active.size() > 1) back(45, () -> plugin.getMenus().openJournal(viewer));
        }
        set(49, button(Material.BOOKSHELF, "journal-history"), e -> plugin.getMenus().openHistory(viewer));
    }

    private void drawHunt() {
        Run run = plugin.getHuntManager().currentRun(hunt).orElse(null);
        TagResolver[] progress = progress(hunt);
        List<Component> lore = new ArrayList<>();
        if (hunt.getDescription() != null) lore.add(plugin.getMessages().raw(hunt.getDescription()));
        lore.addAll(plugin.getMessages().lines("gui.journal-progress-lore", progress));
        set(4, ItemBuilder.of(HuntListMenu.icon(plugin, hunt)).lore(lore).build());

        int found = run == null ? 0 : plugin.getProgressService().found(run, viewer.getUniqueId(), hunt);
        int total = plugin.getHuntManager().liveTotal(hunt);
        Milestone next = null;
        int reached = 0;
        for (Milestone milestone : plugin.getHuntManager().milestones(hunt)) {
            if (milestone.isReached(found, total)) {
                reached++;
            } else if (next == null) {
                next = milestone;
            }
        }
        if (next != null) {
            List<Component> rewards = new ArrayList<>();
            for (Reward reward : next.getRewards()) {
                rewards.add(plugin.getMessages().line("gui.reward-line",
                        Messages.text("type", reward.type().name().toLowerCase(Locale.ROOT).replace('_', ' ')),
                        Messages.text("reward", plugin.getRewardService().describe(reward))));
            }
            int target = next.isAll() ? total : next.getThreshold();
            set(22, ItemBuilder.of(Material.GOLD_NUGGET)
                    .name(name("journal-next", Messages.text("target", target)))
                    .lore(rewards).build());
        } else {
            set(22, button(Material.GOLD_INGOT, "journal-all-paid"));
        }

        List<Head> clues = plugin.getHuntManager().heads(hunt).stream()
                .filter(h -> h.getClue() != null && h.isLive()).sorted(Comparator.comparingInt(Head::getNumber)).toList();
        int unlocked = unlockedClues(clues.size(), found, reached);
        for (int i = 0; i < Math.min(CLUE_SLOTS.length, unlocked); i++) {
            Head head = clues.get(i);
            boolean done = run != null && plugin.getProgressService().hasFound(run, viewer.getUniqueId(), head);
            set(CLUE_SLOTS[i], ItemBuilder.of(done ? Material.ENCHANTED_BOOK : Material.BOOK)
                    .name(name(done ? "clue-found" : "clue", Messages.text("index", i + 1)))
                    .lore(List.of(plugin.getMessages().line("gui.clue-text", Messages.text("clue", head.getClue()))))
                    .build());
        }
        if (clues.size() > unlocked) {
            set(unlocked < CLUE_SLOTS.length ? CLUE_SLOTS[unlocked] : 44, button(Material.GRAY_DYE, "clue-locked",
                    Messages.text("count", clues.size() - unlocked)));
        }
    }

    private int unlockedClues(int clues, int found, int reached) {
        ClueMode mode = ClueMode.parse(hunt.settingString(HuntSetting.CLUE_MODE));
        int unlocked = switch (mode) {
            case ALL -> clues;
            case EVERY_N -> 1 + found / Math.max(1, hunt.settingInt(HuntSetting.CLUE_EVERY));
            case MILESTONE -> 1 + reached;
        };
        return Math.min(clues, unlocked);
    }

    private TagResolver[] progress(Hunt each) {
        Run run = plugin.getHuntManager().currentRun(each).orElse(null);
        int found = run == null ? 0 : plugin.getProgressService().found(run, viewer.getUniqueId(), each);
        int total = plugin.getHuntManager().liveTotal(each);
        int rank = run == null ? 0 : plugin.getProgressService().position(run, viewer.getUniqueId());
        String left = each.getEndAt() == null ? "-" : TimeUtil.formatDuration(each.getEndAt() - System.currentTimeMillis());
        return new TagResolver[]{Messages.hunt(each), Messages.text("found", found), Messages.text("total", total),
                Messages.mini("bar", bar(found, total)), Messages.text("rank", rank == 0 ? "-" : String.valueOf(rank)),
                Messages.text("left", left)};
    }

    static String bar(int found, int total) {
        int filled = total == 0 ? 0 : Math.round(20f * found / total);
        return "<#8FAA87>" + "|".repeat(filled) + "<#6C757D>" + "|".repeat(20 - filled);
    }
}
