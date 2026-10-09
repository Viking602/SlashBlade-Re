package mods.flammpfeil.slashblade.network;

import java.util.UUID;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** An empty-space left click has no vanilla attack-entity packet. */
public record BladeAttackMessage(UUID blade) implements CustomPacketPayload {
    public static final Type<BladeAttackMessage> TYPE=new Type<>(mods.flammpfeil.slashblade.SlashBlade.id("empty_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BladeAttackMessage> STREAM_CODEC=StreamCodec.of(
            (buffer,message)->buffer.writeUUID(message.blade()),buffer->new BladeAttackMessage(buffer.readUUID()));
    @Override public Type<BladeAttackMessage> type() { return TYPE; }
    public static void handle(BladeAttackMessage message,net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> { if(context.player() instanceof ServerPlayer player)apply(message,player); });
    }
    public static void apply(BladeAttackMessage message,ServerPlayer player) {
        if(player.isSpectator() || player.isUsingItem())return;
        var stack=player.getMainHandItem();
        if(!(stack.getItem() instanceof ItemSlashBlade blade))return;
        if(!SBData.get(stack,ItemSlashBlade.BLADESTATE).map(s->s.getUniqueId().equals(message.blade())).orElse(false))return;
        blade.onLeftClickEntity(stack,player,null);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
}
