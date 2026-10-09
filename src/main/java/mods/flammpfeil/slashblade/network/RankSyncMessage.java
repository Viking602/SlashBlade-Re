package mods.flammpfeil.slashblade.network;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class RankSyncMessage implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RankSyncMessage> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("slashblade", "rank_sync"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RankSyncMessage> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.of((buffer, message) -> encode(message, buffer), RankSyncMessage::decode);
    @Override public Type<RankSyncMessage> type() { return TYPE; }

    public long rawPoint;


    public RankSyncMessage(){}

    static public RankSyncMessage decode(FriendlyByteBuf buf) {
        RankSyncMessage msg = new RankSyncMessage();
        msg.rawPoint = buf.readLong();
        return msg;
    }

    static public void encode(RankSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.rawPoint);
    }

}
