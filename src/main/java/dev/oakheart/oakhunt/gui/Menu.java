package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base chest menu: draws into its own inventory and routes clicks by slot. Titles are plain text;
 * button names are title case and lore is small caps (house style), all from messages.yml.
 */
public abstract class Menu implements InventoryHolder {

    protected final OakHunt plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(OakHunt plugin, Player viewer, int rows, String titleKey, TagResolver... resolvers) {
        this.plugin = plugin;
        this.viewer = viewer;
        Component title = plugin.getMessages().line(titleKey, resolvers);
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    protected abstract void draw();

    public void open() {
        redraw();
        viewer.openInventory(inventory);
    }

    public void redraw() {
        inventory.clear();
        actions.clear();
        draw();
        fillEmpty();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void handle(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) action.accept(event);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** A button whose name and lore come from {@code gui.<key>-name} and {@code gui.<key>-lore}. */
    protected ItemStack button(Material material, String key, TagResolver... resolvers) {
        return ItemBuilder.of(material).name(name(key, resolvers)).lore(lore(key, resolvers)).build();
    }

    protected ItemStack button(ItemStack base, String key, TagResolver... resolvers) {
        return ItemBuilder.of(base).name(name(key, resolvers)).lore(lore(key, resolvers)).build();
    }

    protected Component name(String key, TagResolver... resolvers) {
        return plugin.getMessages().line("gui." + key + "-name", resolvers);
    }

    protected List<Component> lore(String key, TagResolver... resolvers) {
        return plugin.getMessages().lines("gui." + key + "-lore", resolvers);
    }

    protected void back(int slot, Runnable to) {
        set(slot, button(Material.ARROW, "back"), e -> to.run());
    }

    /** Previous/next page arrows when there is more than one page. */
    protected void pager(int page, int pages, int previousSlot, int nextSlot, Consumer<Integer> go) {
        if (page > 0) set(previousSlot, button(Material.SPECTRAL_ARROW, "previous-page"), e -> go.accept(page - 1));
        if (page < pages - 1) set(nextSlot, button(Material.SPECTRAL_ARROW, "next-page"), e -> go.accept(page + 1));
    }

    protected static int pages(int size, int perPage) {
        return Math.max(1, (size + perPage - 1) / perPage);
    }

    private void fillEmpty() {
        ItemStack pane = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, pane);
        }
    }
}
