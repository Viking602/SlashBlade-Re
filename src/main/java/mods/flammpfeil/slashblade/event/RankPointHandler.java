package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public class RankPointHandler {
    private static final class SingletonHolder {
        private static final RankPointHandler instance = new RankPointHandler();
    }
    public static RankPointHandler getInstance() {
        return SingletonHolder.instance;
    }
    private RankPointHandler(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Not reached if canceled.
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeathEvent(LivingIncomingDamageEvent event) {

        LivingEntity victim = event.getEntity();
        if(victim != null)
            SBData.get(victim, CapabilityConcentrationRank.RANK_POINT).ifPresent(cr->cr.addRankPoint(victim, -cr.getUnitCapacity()));


        Entity trueSource = event.getSource().getEntity();
        if (!(trueSource instanceof LivingEntity)) return;
        SBData.get(trueSource, CapabilityConcentrationRank.RANK_POINT).ifPresent(cr->cr.addRankPoint(event.getSource()));
    }
}
