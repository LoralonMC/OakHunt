package dev.oakheart.oakhunt.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Small fluent builder for menu icons.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private Component name;
    private final List<Component> lore = new ArrayList<>();
    private boolean glint;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item.clone());
    }

    public ItemBuilder name(Component name) {
        this.name = name;
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(99, amount)));
        return this;
    }

    public ItemBuilder glint(boolean glint) {
        this.glint = glint;
        return this;
    }

    public ItemStack build() {
        item.editMeta(meta -> {
            if (name != null) meta.displayName(name);
            if (!lore.isEmpty()) meta.lore(lore);
            if (glint) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        });
        return item;
    }
}
