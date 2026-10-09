package mods.flammpfeil.slashblade.event;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class AllowFlightOverrwrite {

    private static final class SingletonHolder {
        private static final AllowFlightOverrwrite instance = new AllowFlightOverrwrite();
    }
    public static AllowFlightOverrwrite getInstance() {
        return AllowFlightOverrwrite.SingletonHolder.instance;
    }
    private AllowFlightOverrwrite(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onFMLServerAboutToStartEvent(ServerAboutToStartEvent event){
        if (event.getServer() instanceof net.minecraft.server.dedicated.DedicatedServer server) server.setAllowFlight(true);
    }
}
