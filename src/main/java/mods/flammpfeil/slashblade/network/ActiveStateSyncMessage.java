package mods.flammpfeil.slashblade.network;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

import java.util.function.Supplier;

public class ActiveStateSyncMessage implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<ActiveStateSyncMessage> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("slashblade", "active_state"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ActiveStateSyncMessage> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.of((buffer, message) -> encode(message, buffer), ActiveStateSyncMessage::decode);
    @Override public Type<ActiveStateSyncMessage> type() { return TYPE; }

    public CompoundTag activeTag;
    public int id;


    public ActiveStateSyncMessage(){}

    static public ActiveStateSyncMessage decode(FriendlyByteBuf buf) {
        ActiveStateSyncMessage msg = new ActiveStateSyncMessage();
        msg.id = buf.readInt();
        msg.activeTag = buf.readNbt();
        return msg;
    }

    static public void encode(ActiveStateSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.id);
        buf.writeNbt(msg.activeTag);
    }

}
