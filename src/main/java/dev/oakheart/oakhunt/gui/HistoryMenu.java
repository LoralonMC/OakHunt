package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.PlayerResult;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Every run the player took part in, newest first, with their result. Includes imported runs.
 */
public class HistoryMenu extends Menu {

    public HistoryMenu(OakHunt plugin, Player viewer) {
        super(plugin, viewer, 6, "gui.history-title");
    }

    @Override
    protected void draw() {
        List<PlayerResult> history = plugin.getProgressService().history(viewer.getUniqueId());
        if (history.isEmpty()) set(22, button(Material.BOOK, "history-none"));
        for (int i = 0; i < Math.min(45, history.size()); i++) {
            PlayerResult result = history.get(i);
            Run run = plugin.getHuntManager().run(result.getRunId()).orElse(null);
            if (run == null) continue;
            Hunt hunt = plugin.getHuntManager().huntIncludingArchived(run.getHuntId()).orElse(null);
            if (hunt == null) continue;
            int rank = plugin.getProgressService().completionRank(run, viewer.getUniqueId());
            String key = rank > 0 ? "gui.history-complete-lore" : "gui.history-partial-lore";
            set(i, ItemBuilder.of(HuntListMenu.icon(plugin, hunt))
                    .lore(plugin.getMessages().lines(key, Messages.text("month", TimeUtil.formatMonth(run.getStartedAt())),
                            Messages.text("found", result.getFound()), Messages.text("rank", rank),
                            Messages.text("completions", plugin.getProgressService().completions(run)),
                            Messages.text("position", plugin.getProgressService().position(run, viewer.getUniqueId()))))
                    .glint(rank > 0)
                    .build());
        }
        back(49, () -> plugin.getMenus().openJournal(viewer));
    }
}
