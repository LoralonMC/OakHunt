package dev.oakheart.oakhunt.hook;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.PlayerResult;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * {@code %oakhunt_<hunt>_<field>%}. Every value is a plain number or word. Hunt ids have no
 * underscores, so the first underscore splits hunt from field.
 */
public class OakHuntExpansion extends PlaceholderExpansion {

    private final OakHunt plugin;

    public OakHuntExpansion(OakHunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "oakhunt";
    }

    @Override
    public String getAuthor() {
        return "Loralon";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equals("active")) return String.valueOf(plugin.getHuntManager().activeHunts().size());
        int split = params.indexOf('_');
        if (split <= 0) return null;
        Hunt hunt = plugin.getHuntManager().hunt(params.substring(0, split)).orElse(null);
        if (hunt == null) return "";
        String field = params.substring(split + 1);
        Run run = plugin.getHuntManager().lastRun(hunt).orElse(null);
        int total = plugin.getHuntManager().liveTotal(hunt);
        long now = System.currentTimeMillis();

        if (field.startsWith("top_")) {
            String[] parts = field.split("_");
            if (parts.length != 3) return null;
            int rank;
            try {
                rank = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            List<PlayerResult> ranking = run == null ? List.of() : plugin.getProgressService().ranking(run);
            if (rank < 1 || rank > ranking.size()) return "-";
            PlayerResult result = ranking.get(rank - 1);
            return switch (parts[2]) {
                case "name" -> result.getName();
                case "value" -> String.valueOf(result.getFound());
                default -> null;
            };
        }
        return switch (field) {
            case "total" -> String.valueOf(total);
            case "state" -> plugin.getHuntManager().state(hunt).key();
            case "starts_in" -> hunt.getStartAt() == null ? "" : TimeUtil.formatDuration(hunt.getStartAt() - now);
            case "ends_in" -> hunt.getEndAt() == null ? "" : TimeUtil.formatDuration(hunt.getEndAt() - now);
            case "found", "remaining", "percent", "position" -> {
                if (player == null) yield "";
                int found = run == null ? 0 : plugin.getProgressService().found(run, player.getUniqueId(), hunt);
                yield switch (field) {
                    case "found" -> String.valueOf(found);
                    case "remaining" -> String.valueOf(Math.max(0, total - found));
                    case "percent" -> String.valueOf(total == 0 ? 0 : Math.round(100f * found / total));
                    default -> {
                        int position = run == null ? 0 : plugin.getProgressService().position(run, player.getUniqueId());
                        yield position == 0 ? "-" : String.valueOf(position);
                    }
                };
            }
            default -> null;
        };
    }
}
