package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A yes/no (or option A / option B) menu. A null action just closes the menu.
 */
public class ChoiceMenu extends Menu {

    private final String key;
    private final Runnable yes;
    private final Runnable no;

    public ChoiceMenu(OakHunt plugin, Player viewer, String key, Runnable yes, Runnable no) {
        super(plugin, viewer, 3, "gui." + key + "-title");
        this.key = key;
        this.yes = yes;
        this.no = no;
    }

    @Override
    protected void draw() {
        set(11, button(Material.LIME_STAINED_GLASS_PANE, key + "-yes"), e -> run(yes));
        set(15, button(Material.RED_STAINED_GLASS_PANE, key + "-no"), e -> run(no));
    }

    private void run(Runnable action) {
        viewer.closeInventory();
        if (action != null) action.run();
    }
}
