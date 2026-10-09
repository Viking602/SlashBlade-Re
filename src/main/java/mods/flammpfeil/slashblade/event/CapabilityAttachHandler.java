package mods.flammpfeil.slashblade.event;
import mods.flammpfeil.slashblade.compat.SBData;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
/** Initializes independent attachments for each living entity entering a level. */
public final class CapabilityAttachHandler {
    @SubscribeEvent public void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            entity.getData(SBData.INPUT);
            entity.getData(SBData.EFFECT);
            entity.getData(SBData.RANK);
        }
    }
}
