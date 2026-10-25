package dev.oakheart.oakhunt.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import dev.oakheart.oakhunt.OakHunt;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Catches clicks on an editor's fake preview heads. The server has no block there, so the clicks
 * would otherwise be processed against air: a dig does nothing and a use places the held item
 * inside the preview. The packet is dropped, acknowledged so the client's prediction settles, and
 * handed to the main thread as an editor click.
 *
 * <p>Runs on Netty threads: only {@code EditorService#previewAt} (a concurrent index) is touched here.
 */
public class PacketClickListener implements PacketListener {

    private final OakHunt plugin;

    public PacketClickListener(OakHunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = dig.getAction();
            if (action != DiggingAction.START_DIGGING && action != DiggingAction.FINISHED_DIGGING
                    && action != DiggingAction.CANCELLED_DIGGING) return;
            Vector3i pos = dig.getBlockPosition();
            UUID uuid = event.getUser().getUUID();
            Integer headId = uuid == null ? null : plugin.getEditorService().previewAt(uuid, pos.getX(), pos.getY(), pos.getZ());
            if (headId == null) return;
            event.setCancelled(true);
            event.getUser().sendPacket(new WrapperPlayServerAcknowledgeBlockChanges(dig.getSequence()));
            boolean start = action == DiggingAction.START_DIGGING;
            Bukkit.getScheduler().runTask(plugin, () -> handle(uuid, headId, true, start, pos, null));
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement use = new WrapperPlayClientPlayerBlockPlacement(event);
            Vector3i pos = use.getBlockPosition();
            UUID uuid = event.getUser().getUUID();
            Integer headId = uuid == null ? null : plugin.getEditorService().previewAt(uuid, pos.getX(), pos.getY(), pos.getZ());
            if (headId == null) return;
            event.setCancelled(true);
            event.getUser().sendPacket(new WrapperPlayServerAcknowledgeBlockChanges(use.getSequence()));
            boolean mainHand = use.getHand() == InteractionHand.MAIN_HAND;
            Vector3i adjacent = new Vector3i(pos.getX() + use.getFace().getModX(),
                    pos.getY() + use.getFace().getModY(), pos.getZ() + use.getFace().getModZ());
            Bukkit.getScheduler().runTask(plugin, () -> handle(uuid, headId, false, mainHand, pos, adjacent));
        }
    }

    /**
     * @param act for digs, whether this is the start of the dig; for uses, whether it was the main hand
     */
    private void handle(UUID uuid, int headId, boolean dig, boolean act, Vector3i pos, Vector3i adjacent) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        if (adjacent != null) {
            // The client may have predicted placing its held block next to the preview: undo that.
            Block block = player.getWorld().getBlockAt(adjacent.getX(), adjacent.getY(), adjacent.getZ());
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }
        plugin.getHuntManager().head(headId).ifPresentOrElse(head -> {
            if (act) {
                plugin.getEditorActions().click(player, head, dig);
            } else {
                plugin.getEditorService().resendFake(player, head);
            }
        }, () -> {
            Block block = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        });
    }
}
