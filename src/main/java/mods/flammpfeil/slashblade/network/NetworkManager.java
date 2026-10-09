package mods.flammpfeil.slashblade.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class NetworkManager {

    public static void register(RegisterPayloadHandlersEvent event){
        var registrar = event.registrar("26.1.2-2");
        registrar.playToServer(BladeAttackMessage.TYPE,BladeAttackMessage.STREAM_CODEC,BladeAttackMessage::handle);
        registrar.playToServer(MoveCommandMessage.TYPE, MoveCommandMessage.STREAM_CODEC, MoveCommandMessage::handle);
        registrar.playToClient(ActiveStateSyncMessage.TYPE, ActiveStateSyncMessage.STREAM_CODEC);
        registrar.playToClient(RankSyncMessage.TYPE, RankSyncMessage.STREAM_CODEC);
        registrar.playToClient(MotionBroadcastMessage.TYPE, MotionBroadcastMessage.STREAM_CODEC);
    }

}
