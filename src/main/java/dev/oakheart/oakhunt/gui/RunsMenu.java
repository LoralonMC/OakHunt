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
 * Every run of a hunt: dates, finders, completions and the top three.
 */
public class RunsMenu extends Menu {

    private final Hunt hunt;

    public RunsMenu(OakHunt plugin, Player viewer, Hunt hunt) {
        super(plugin, viewer, 3, "gui.runs-title", Messages.text("hunt_id", hunt.getId()));
        this.hunt = hunt;
    }

    @Override
    protected void draw() {
        List<Run> runs = plugin.getHuntManager().runs(hunt).reversed();
        for (int i = 0; i < Math.min(18, runs.size()); i++) {
            Run run = runs.get(i);
            List<PlayerResult> ranking = plugin.getProgressService().ranking(run);
            set(i, ItemBuilder.of(run.isOpen() ? Material.CLOCK : Material.BOOK)
                    .name(plugin.getMessages().line("gui.run-icon-name", Messages.text("number", run.getNumber())))
                    .lore(plugin.getMessages().lines(run.isImported() ? "gui.run-icon-imported-lore" : "gui.run-icon-lore",
                            Messages.text("started", TimeUtil.formatUtc(run.getStartedAt())),
                            Messages.text("ended", run.getEndedAt() == null ? "running" : TimeUtil.formatUtc(run.getEndedAt())),
                            Messages.text("finders", ranking.size()),
                            Messages.text("completions", plugin.getProgressService().completions(run)),
                            Messages.text("first", name(ranking, 0)), Messages.text("second", name(ranking, 1)),
                            Messages.text("third", name(ranking, 2))))
                    .glint(run.isOpen())
                    .build());
        }
        back(18, () -> plugin.getMenus().openHunt(viewer, hunt));
    }

    private static String name(List<PlayerResult> ranking, int index) {
        return index < ranking.size() ? ranking.get(index).getName() : "-";
    }
}
