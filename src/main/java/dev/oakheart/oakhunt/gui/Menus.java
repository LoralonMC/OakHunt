package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import org.bukkit.entity.Player;

/**
 * Entry points for every menu, so menus open each other without knowing constructors.
 */
public class Menus {

    private final OakHunt plugin;

    public Menus(OakHunt plugin) {
        this.plugin = plugin;
    }

    public void openHuntList(Player player, int page) {
        new HuntListMenu(plugin, player, page).open();
    }

    public void openHunt(Player player, Hunt hunt) {
        new HuntMenu(plugin, player, hunt).open();
    }

    public void openHeads(Player player, Hunt hunt, int page) {
        new HeadsMenu(plugin, player, hunt, page).open();
    }

    public void openHead(Player player, Hunt hunt, Head head) {
        new HeadMenu(plugin, player, hunt, head).open();
    }

    public void openSkins(Player player, Hunt hunt, int page) {
        new SkinsMenu(plugin, player, hunt, page).open();
    }

    public void openRewards(Player player, Hunt hunt) {
        new RewardsMenu(plugin, player, hunt).open();
    }

    public void openMilestone(Player player, Hunt hunt, Milestone milestone) {
        new MilestoneMenu(plugin, player, hunt, milestone).open();
    }

    public void openRewardType(Player player, Hunt hunt, Milestone milestone) {
        new RewardTypeMenu(plugin, player, hunt, milestone).open();
    }

    public void openKeyPicker(Player player, Hunt hunt, Milestone milestone) {
        new KeyPickerMenu(plugin, player, hunt, milestone).open();
    }

    public void openTagPicker(Player player, Hunt hunt, Milestone milestone, int page) {
        new TagPickerMenu(plugin, player, hunt, milestone, page).open();
    }

    public void openSettings(Player player, Hunt hunt) {
        new SettingsMenu(plugin, player, hunt).open();
    }

    public void openRuns(Player player, Hunt hunt) {
        new RunsMenu(plugin, player, hunt).open();
    }

    public void openJournal(Player player) {
        new JournalMenu(plugin, player, null).open();
    }

    public void openJournal(Player player, Hunt hunt) {
        new JournalMenu(plugin, player, hunt).open();
    }

    public void openHistory(Player player) {
        new HistoryMenu(plugin, player).open();
    }

    /**
     * Two-option choice (confirm/cancel, or two named options).
     *
     * @param key messages.yml prefix: {@code gui.<key>-title}, {@code gui.<key>-yes-*}, {@code gui.<key>-no-*}
     */
    public void choose(Player player, String key, Runnable yes, Runnable no) {
        new ChoiceMenu(plugin, player, key, yes, no).open();
    }
}
