package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.network.MotionBroadcastMessage;
import mods.flammpfeil.slashblade.network.NetworkManager;
import mods.flammpfeil.slashblade.network.RankSyncMessage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class BladeMotionEventBroadcaster {

    private static final class SingletonHolder {
        private static final BladeMotionEventBroadcaster instance = new BladeMotionEventBroadcaster();
    }
    public static BladeMotionEventBroadcaster getInstance() {
        return BladeMotionEventBroadcaster.SingletonHolder.instance;
    }
    private BladeMotionEventBroadcaster(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onBladeMotion(BladeMotionEvent event){
        if(!(event.getEntity() instanceof ServerPlayer)) return;

        ServerPlayer sp = (ServerPlayer) event.getEntity();

        MotionBroadcastMessage msg = new MotionBroadcastMessage();
        msg.playerId = sp.getUUID();
        msg.combo = event.getCombo().getName();

        //if(msg.combo == Extra.EX_JUDGEMENT_CUT.getName())
        {
            PacketDistributor.sendToPlayersNear(sp.level(), null, sp.getX(), sp.getY(), sp.getZ(), 20, msg);
        }

    }
}
