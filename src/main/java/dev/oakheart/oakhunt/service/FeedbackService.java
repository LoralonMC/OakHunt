package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.config.ConfigManager;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.HuntSetting;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sounds, particles, titles and hints. Particles and sounds go to the one player they concern, so
 * nothing in the world points at an unfound head.
 */
public class FeedbackService {

    private final OakHunt plugin;
    private final NamespacedKey fireworkKey;
    private BukkitTask ambientTask;
    private BukkitTask hintTask;

    public FeedbackService(OakHunt plugin) {
        this.plugin = plugin;
        this.fireworkKey = new NamespacedKey(plugin, "firework");
    }

    public void start() {
        stop();
        ConfigManager config = plugin.getConfigManager();
        ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, this::ambientTick, 40L, config.getAmbientInterval());
        hintTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hintTick, 40L, 20L);
    }

    public void stop() {
        if (ambientTask != null) ambientTask.cancel();
        if (hintTask != null) hintTask.cancel();
    }

    public NamespacedKey getFireworkKey() {
        return fireworkKey;
    }

    public void newFind(Player player, Location at, Hunt hunt, ProgressService.FindResult result) {
        play(player, at, plugin.getConfigManager().getNewFind());
        TagResolver[] resolvers = progress(hunt, result.found(), result.total());
        plugin.getMessages().send(player, "find-new", resolvers);
        if (hunt.setting(HuntSetting.TITLE_ON_FIND) && !result.completedNow()) {
            player.showTitle(Title.title(plugin.getMessages().line("find-title", resolvers),
                    plugin.getMessages().line("find-subtitle", resolvers),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))));
        }
    }

    public void alreadyFound(Player player, Location at, Hunt hunt, ProgressService.FindResult result) {
        play(player, at, plugin.getConfigManager().getAlreadyFound());
        plugin.getMessages().send(player, "find-already", progress(hunt, result.found(), result.total()));
    }

    public void completed(Player player, Hunt hunt, int rank) {
        TagResolver[] resolvers = {Messages.hunt(hunt), Messages.text("rank", rank), Messages.text("player", player.getName())};
        play(player, player.getLocation(), plugin.getConfigManager().getCompletion());
        player.showTitle(Title.title(plugin.getMessages().line("complete-title", resolvers),
                plugin.getMessages().line("complete-subtitle", resolvers),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(700))));
        if (hunt.setting(HuntSetting.COMPLETION_FIREWORK)) firework(player.getLocation());
        if (rank == 1 && hunt.setting(HuntSetting.BROADCAST_FIRST_FINISHER)) {
            Bukkit.broadcast(plugin.getMessages().line("first-finisher", resolvers));
        }
    }

    public void milestone(Player player) {
        play(player, player.getLocation(), plugin.getConfigManager().getMilestone());
    }

    private void play(Player player, Location at, ConfigManager.Effect effect) {
        if (!effect.sound().isBlank()) player.playSound(at, effect.sound(), effect.volume(), effect.pitch());
        if (effect.particle() != null && effect.particleCount() > 0) {
            player.spawnParticle(effect.particle(), at.clone().add(0, 0.5, 0), effect.particleCount(), 0.3, 0.3, 0.3, 0.05);
        }
    }

    private void firework(Location at) {
        at.getWorld().spawn(at, Firework.class, firework -> {
            firework.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(0xB8E6D5), Color.fromRGB(0xFCD472))
                    .withFade(Color.fromRGB(0xF2EBD7)).trail(true).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }

    /** Soft particles above heads the player has already found, visible only to them. */
    private void ambientTick() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isAmbientEnabled()) return;
        double radiusSquared = config.getAmbientRadius() * config.getAmbientRadius();
        for (Hunt hunt : plugin.getHuntManager().activeHunts()) {
            Run run = plugin.getHuntManager().currentRun(hunt).orElse(null);
            if (run == null) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Set<Integer> found = plugin.getProgressService().foundHeads(run, player.getUniqueId());
                if (found.isEmpty()) continue;
                Location eye = player.getLocation();
                String world = eye.getWorld().getName();
                for (int id : found) {
                    Head head = plugin.getHuntManager().head(id).orElse(null);
                    if (head == null || !head.isLive() || !head.getWorld().equals(world)) continue;
                    double dx = head.getX() + 0.5 - eye.getX();
                    double dy = head.getY() + 0.5 - eye.getY();
                    double dz = head.getZ() + 0.5 - eye.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    player.spawnParticle(config.getAmbientParticle(),
                            head.getX() + 0.5, head.getY() + 0.9, head.getZ() + 0.5,
                            config.getAmbientCount(), 0.15, 0.1, 0.15, 0);
                }
            }
        }
    }

    /** For hunts with hints on: a small chance each second near an unfound head. */
    private void hintTick() {
        ConfigManager config = plugin.getConfigManager();
        double distanceSquared = config.getHintDistance() * config.getHintDistance();
        for (Hunt hunt : plugin.getHuntManager().activeHunts()) {
            if (!hunt.setting(HuntSetting.HINTS)) continue;
            Run run = plugin.getHuntManager().currentRun(hunt).orElse(null);
            if (run == null) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("oakhunt.use")) continue;
                if (ThreadLocalRandom.current().nextInt(config.getHintFrequency()) != 0) continue;
                Location at = player.getLocation();
                String world = at.getWorld().getName();
                boolean near = false;
                for (Head head : plugin.getHuntManager().heads(hunt)) {
                    if (!head.isLive() || !head.getWorld().equals(world)) continue;
                    double dx = head.getX() + 0.5 - at.getX();
                    double dy = head.getY() + 0.5 - at.getY();
                    double dz = head.getZ() + 0.5 - at.getZ();
                    if (dx * dx + dy * dy + dz * dz > distanceSquared) continue;
                    if (plugin.getProgressService().hasFound(run, player.getUniqueId(), head)) continue;
                    near = true;
                    break;
                }
                if (!near) continue;
                ConfigManager.Effect hint = config.getHint();
                if (!hint.sound().isBlank()) player.playSound(at, hint.sound(), hint.volume(), hint.pitch());
                plugin.getMessages().send(player, "hint", Messages.hunt(hunt));
            }
        }
    }

    private static TagResolver[] progress(Hunt hunt, int found, int total) {
        return new TagResolver[]{Messages.hunt(hunt), Messages.text("found", found), Messages.text("total", total)};
    }
}
