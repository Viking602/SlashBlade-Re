package mods.flammpfeil.slashblade.client;

import mods.flammpfeil.slashblade.capability.concentrationrank.*;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client handlers are registered only on the physical client. */
public final class ClientNetwork {
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(ActiveStateSyncMessage.TYPE, (message, context) -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var id = message.activeTag.read("BladeUniqueId", net.minecraft.core.UUIDUtil.CODEC);
            if (id.isEmpty()) return;
            if (level.getEntity(message.id) instanceof LivingEntity target) {
                SBData.get(target.getMainHandItem(), ItemSlashBlade.BLADESTATE)
                    .filter(state -> state.getUniqueId().equals(id.get()))
                    .ifPresent(state -> state.setActiveState(message.activeTag));
            }
        });
        event.register(RankSyncMessage.TYPE, (message, context) -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            SBData.get(player, CapabilityConcentrationRank.RANK_POINT).ifPresent(rank -> {
                long time = player.level().getGameTime();
                var previous = rank.getRank(time);
                rank.setRawRankPoint(message.rawPoint);
                rank.setLastUpdte(time);
                if (previous.level < rank.getRank(time).level) rank.setLastRankRise(time);
            });
        });
        event.register(MotionBroadcastMessage.TYPE, (message, context) -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var player = level.getPlayerByUUID(message.playerId);
            var combo = ComboState.NONE.valueOf(message.combo);
            if (player != null && combo != null) NeoForge.EVENT_BUS.post(new BladeMotionEvent(player, combo));
        });
    }
    private ClientNetwork() {}
}
