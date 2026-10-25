package dev.oakheart.oakhunt.service;

import dev.oakheart.config.ConfigManager;
import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.PlayerResult;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One-off import of a past hunt from {@code plugins/OakHunt/import/<file>.yml}: the hunt, its
 * heads, one closed run and per-player results. Nothing is placed in the world; the hunt arrives
 * ended. Block-style YAML only (OakheartLib doesn't read inline flow maps).
 */
public class ImportService {

    private final OakHunt plugin;
    private final File folder;

    public ImportService(OakHunt plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "import");
    }

    public List<String> files() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 4));
        }
        return names;
    }

    public void run(CommandSender sender, String name) {
        File file = new File(folder, name.endsWith(".yml") ? name : name + ".yml");
        if (!file.isFile()) {
            plugin.getMessages().cmd(sender, "import-missing", Messages.text("file", file.getName()));
            return;
        }
        ConfigManager data;
        try {
            data = ConfigManager.load(file.toPath());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read import file " + file.getName(), e);
            plugin.getMessages().cmd(sender, "import-failed", Messages.text("reason", "unreadable file, see console"));
            return;
        }
        String id = data.getString("hunt.id", "");
        if (!HuntManager.ID_PATTERN.matcher(id).matches()) {
            plugin.getMessages().cmd(sender, "import-failed", Messages.text("reason", "invalid hunt.id"));
            return;
        }
        if (plugin.getHuntManager().huntIncludingArchived(id).isPresent()) {
            plugin.getMessages().cmd(sender, "hunt-exists", Messages.text("hunt_id", id));
            return;
        }
        String texture = skin(data.getString("skin", ""));
        if (texture == null) {
            plugin.getMessages().cmd(sender, "import-failed", Messages.text("reason", "skin could not be resolved (is HeadDatabase loaded?)"));
            return;
        }
        long startedAt = data.getLong("run.started-at", 0);
        long endedAt = data.getLong("run.ended-at", 0);
        if (startedAt <= 0 || endedAt < startedAt) {
            plugin.getMessages().cmd(sender, "import-failed", Messages.text("reason", "run.started-at / run.ended-at missing or out of order"));
            return;
        }

        Hunt hunt = plugin.getHuntManager().createHunt(id, sender.getName());
        hunt.setDisplayName(data.getString("hunt.display-name", hunt.getDisplayName()));
        String description = data.getString("hunt.description", "");
        hunt.setDescription(description.isBlank() ? null : description);
        plugin.getHuntManager().saveHunt(hunt);
        plugin.getHuntManager().addSkin(hunt, texture, data.getString("skin", "import"), sender.getName());

        int heads = 0;
        for (Map<String, Object> entry : data.getMapList("heads")) {
            String world = String.valueOf(entry.getOrDefault("world", "world"));
            int x = number(entry.get("x"));
            int y = number(entry.get("y"));
            int z = number(entry.get("z"));
            String blockData = String.valueOf(entry.getOrDefault("block-data", "minecraft:player_head[rotation=0]"));
            Object headSkin = entry.get("skin");
            String headTexture = headSkin == null ? texture : skin(String.valueOf(headSkin));
            plugin.getHuntManager().importHead(hunt, world, x, y, z, blockData, headTexture != null ? headTexture : texture,
                    entry.get("clue") == null ? null : String.valueOf(entry.get("clue")));
            heads++;
        }

        Run run = plugin.getHuntManager().importRun(hunt, startedAt, endedAt);
        int results = 0;
        for (Map<String, Object> entry : data.getMapList("results")) {
            try {
                UUID uuid = UUID.fromString(String.valueOf(entry.get("uuid")));
                int found = number(entry.get("found"));
                Object completed = entry.get("completed-at");
                Long completedAt = completed == null ? null : Long.parseLong(String.valueOf(completed));
                long last = completedAt != null ? completedAt : endedAt;
                plugin.getProgressService().importResult(new PlayerResult(run.getId(), uuid,
                        String.valueOf(entry.getOrDefault("name", "?")), found, last, completedAt));
                results++;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping import result " + entry + ": " + e.getMessage());
            }
        }
        plugin.getDatabase().audit(sender.getName(), id, "import", file.getName() + ": " + heads + " heads, " + results + " results");
        plugin.getMessages().cmd(sender, "import-done", Messages.hunt(hunt), Messages.text("heads", heads),
                Messages.text("results", results));
    }

    /** "hdb:&lt;id&gt;", or a texture value/hash. */
    private String skin(String source) {
        if (source.toLowerCase().startsWith("hdb:")) {
            return SkinUtil.textureOf(plugin.getHeadDatabaseHook().head(source.substring(4)));
        }
        return SkinUtil.normalizeTexture(source);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
