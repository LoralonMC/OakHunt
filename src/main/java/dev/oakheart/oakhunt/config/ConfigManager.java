package dev.oakheart.oakhunt.config;

import dev.oakheart.config.ConfigMigrator;
import dev.oakheart.oakhunt.OakHunt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads, evolves, validates and caches config.yml. Messages live in messages.yml, owned by
 * OakheartLib's MessageManager.
 */
public class ConfigManager {

    private final OakHunt plugin;
    private final Logger logger;
    private final File configFile;

    private dev.oakheart.config.ConfigManager config;

    private boolean debug;
    private Set<Material> replaceable;
    private long clickCooldownMs;
    private Effect newFind;
    private Effect alreadyFound;
    private Effect milestone;
    private Effect completion;
    private Effect hint;
    private boolean ambientEnabled;
    private Particle ambientParticle;
    private int ambientCount;
    private double ambientRadius;
    private int ambientInterval;
    private double hintDistance;
    private int hintFrequency;
    private double previewRadius;
    private double labelHeight;
    private boolean coreProtectEnabled;
    private String keysFolder;

    public ConfigManager(OakHunt plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        try {
            config = dev.oakheart.config.ConfigManager.load(configFile.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.yml", e);
        }
        evolve(config);
        if (!validate(config)) {
            throw new IllegalStateException("config.yml failed validation, see the warnings above");
        }
        cacheValues();
    }

    /**
     * @return true if the new config was valid and applied
     */
    public boolean reload() {
        try {
            dev.oakheart.config.ConfigManager fresh = dev.oakheart.config.ConfigManager.load(configFile.toPath());
            evolve(fresh);
            if (!validate(fresh)) {
                logger.warning("Configuration reload failed validation. Keeping previous configuration.");
                return false;
            }
            config = fresh;
            cacheValues();
            logger.info("Configuration reloaded successfully.");
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to reload config.yml", e);
            return false;
        }
    }

    private void evolve(dev.oakheart.config.ConfigManager target) {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) return;
            var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
            boolean migrated = ConfigMigrator.forConfig(target).run();
            boolean merged = target.mergeDefaults(defaults);
            boolean synced = target.syncComments(defaults,
                    plugin.getDataFolder().toPath().resolve(".oakheart/config-baseline.yml"));
            if (migrated || merged || synced) target.save();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to update config.yml with new defaults", e);
        }
    }

    private boolean validate(dev.oakheart.config.ConfigManager c) {
        List<String> warnings = new ArrayList<>();
        for (String name : c.getStringList("hunts.replaceable")) {
            if (Material.matchMaterial(name) == null) warnings.add("hunts.replaceable: unknown material " + name);
        }
        for (String path : List.of("feedback.new-find", "feedback.already-found")) {
            String particle = c.getString(path + ".particle", "");
            if (!particle.isBlank() && resolveParticle(particle) == null) {
                warnings.add(path + ".particle: unknown or data-requiring particle " + particle);
            }
        }
        if (resolveParticle(c.getString("feedback.found-ambient.particle", "composter")) == null) {
            warnings.add("feedback.found-ambient.particle: unknown or data-requiring particle");
        }
        if (c.getInt("feedback.hints.frequency", 20) < 1) warnings.add("feedback.hints.frequency must be at least 1");
        if (c.getInt("feedback.found-ambient.interval-ticks", 20) < 1) {
            warnings.add("feedback.found-ambient.interval-ticks must be at least 1");
        }
        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            warnings.forEach(w -> logger.warning("  - " + w));
        }
        // Every problem above falls back to a safe default, so none is fatal.
        return true;
    }

    private void cacheValues() {
        debug = config.getBoolean("debug", false);
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("hunts.replaceable")) {
            Material material = Material.matchMaterial(name);
            if (material != null) materials.add(material);
        }
        materials.add(Material.AIR);
        replaceable = materials;
        clickCooldownMs = config.getLong("hunts.click-cooldown-ms", 400);
        newFind = effect("feedback.new-find");
        alreadyFound = effect("feedback.already-found");
        milestone = effect("feedback.milestone");
        completion = effect("feedback.completion");
        hint = effect("feedback.hints");
        ambientEnabled = config.getBoolean("feedback.found-ambient.enabled", true);
        Particle ambient = resolveParticle(config.getString("feedback.found-ambient.particle", "composter"));
        ambientParticle = ambient != null ? ambient : Particle.COMPOSTER;
        ambientCount = config.getInt("feedback.found-ambient.particle-count", 2);
        ambientRadius = config.getDouble("feedback.found-ambient.radius", 16);
        ambientInterval = Math.max(1, config.getInt("feedback.found-ambient.interval-ticks", 20));
        hintDistance = config.getDouble("feedback.hints.distance", 16);
        hintFrequency = Math.max(1, config.getInt("feedback.hints.frequency", 20));
        previewRadius = config.getDouble("editor.preview-radius", 48);
        labelHeight = config.getDouble("editor.label-height", 0.9);
        coreProtectEnabled = config.getBoolean("integrations.coreprotect", true);
        keysFolder = config.getString("integrations.phoenixcrates-keys-folder", "PhoenixCrates/keys");
    }

    private Effect effect(String path) {
        Particle particle = resolveParticle(config.getString(path + ".particle", ""));
        return new Effect(config.getString(path + ".sound", ""),
                (float) config.getDouble(path + ".volume", 1.0),
                (float) config.getDouble(path + ".pitch", 1.0),
                particle, config.getInt(path + ".particle-count", 0));
    }

    /** A particle that takes no extra data, or null. */
    private static Particle resolveParticle(String name) {
        if (name == null || name.isBlank()) return null;
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) return null;
        Particle particle = Registry.PARTICLE_TYPE.get(key);
        return particle != null && particle.getDataType() == Void.class ? particle : null;
    }

    /**
     * A sound plus an optional particle burst. Empty sound or null particle means "none".
     */
    public record Effect(String sound, float volume, float pitch, Particle particle, int particleCount) {
    }

    public boolean isDebug() { return debug; }
    public boolean isReplaceable(Material material) { return replaceable.contains(material); }
    public long getClickCooldownMs() { return clickCooldownMs; }
    public Effect getNewFind() { return newFind; }
    public Effect getAlreadyFound() { return alreadyFound; }
    public Effect getMilestone() { return milestone; }
    public Effect getCompletion() { return completion; }
    public Effect getHint() { return hint; }
    public boolean isAmbientEnabled() { return ambientEnabled; }
    public Particle getAmbientParticle() { return ambientParticle; }
    public int getAmbientCount() { return ambientCount; }
    public double getAmbientRadius() { return ambientRadius; }
    public int getAmbientInterval() { return ambientInterval; }
    public double getHintDistance() { return hintDistance; }
    public int getHintFrequency() { return hintFrequency; }
    public double getPreviewRadius() { return previewRadius; }
    public double getLabelHeight() { return labelHeight; }
    public boolean isCoreProtectEnabled() { return coreProtectEnabled; }
    public String getKeysFolder() { return keysFolder; }
}
