package mods.flammpfeil.slashblade.network;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MotionBroadcastMessage implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<MotionBroadcastMessage> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("slashblade", "motion"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, MotionBroadcastMessage> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.of((buffer, message) -> encode(message, buffer), MotionBroadcastMessage::decode);
    @Override public Type<MotionBroadcastMessage> type() { return TYPE; }

    public UUID playerId;
    public String combo;



    public MotionBroadcastMessage(){}

    static public MotionBroadcastMessage decode(FriendlyByteBuf buf) {
        MotionBroadcastMessage msg = new MotionBroadcastMessage();
        msg.playerId = buf.readUUID();
        msg.combo = buf.readUtf(128);
        return msg;
    }

    static public void encode(MotionBroadcastMessage msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeUtf(msg.combo);
    }

}
