package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.ClueMode;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.HuntSetting;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Per-hunt toggles: hints, titles, fireworks, first-finisher broadcast, and how clues unlock.
 */
public class SettingsMenu extends Menu {

    private final Hunt hunt;

    public SettingsMenu(OakHunt plugin, Player viewer, Hunt hunt) {
        super(plugin, viewer, 3, "gui.settings-title", Messages.text("hunt_id", hunt.getId()));
        this.hunt = hunt;
    }

    @Override
    protected void draw() {
        toggle(10, HuntSetting.HINTS, "setting-hints");
        toggle(11, HuntSetting.TITLE_ON_FIND, "setting-title");
        toggle(12, HuntSetting.COMPLETION_FIREWORK, "setting-firework");
        toggle(13, HuntSetting.BROADCAST_FIRST_FINISHER, "setting-first-finisher");
        ClueMode mode = ClueMode.parse(hunt.settingString(HuntSetting.CLUE_MODE));
        set(15, button(Material.WRITABLE_BOOK, "setting-clue-mode", Messages.text("mode", mode.name().toLowerCase().replace('_', ' ')),
                Messages.text("every", hunt.settingInt(HuntSetting.CLUE_EVERY))), e -> {
            if (e.isRightClick()) {
                plugin.getChatPrompt().ask(viewer, "prompt-clue-every", input -> {
                    if (input != null) {
                        try {
                            int every = Integer.parseInt(input.trim());
                            if (every > 0) plugin.getHuntManager().setSetting(hunt, HuntSetting.CLUE_EVERY, String.valueOf(every), viewer.getName());
                        } catch (NumberFormatException ignored) {
                            plugin.getMessages().cmd(viewer, "invalid-amount", Messages.text("input", input));
                        }
                    }
                    plugin.getMenus().openSettings(viewer, hunt);
                });
                return;
            }
            ClueMode next = ClueMode.values()[(mode.ordinal() + 1) % ClueMode.values().length];
            plugin.getHuntManager().setSetting(hunt, HuntSetting.CLUE_MODE, next.name(), viewer.getName());
            redraw();
        });
        back(18, () -> plugin.getMenus().openHunt(viewer, hunt));
    }

    private void toggle(int slot, HuntSetting setting, String key) {
        boolean on = hunt.setting(setting);
        set(slot, button(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, key + (on ? "-on" : "-off")), e -> {
            plugin.getHuntManager().setSetting(hunt, setting, String.valueOf(!on), viewer.getName());
            redraw();
        });
    }
}
