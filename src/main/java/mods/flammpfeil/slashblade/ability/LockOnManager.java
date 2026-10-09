package mods.flammpfeil.slashblade.ability;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.RayTraceHelper;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;

import net.neoforged.bus.api.SubscribeEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class LockOnManager {
    private static final class SingletonHolder {
        private static final LockOnManager instance = new LockOnManager();
    }

    public static LockOnManager getInstance() {
        return SingletonHolder.instance;
    }

    private LockOnManager() {
    }

    public void register() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onInputChange(InputCommandEvent event) {
        if(event.getOld().contains(InputCommand.SNEAK) == event.getCurrent().contains(InputCommand.SNEAK)) return;

        ServerPlayer player = event.getEntity();
        //set target
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ItemSlashBlade)) return;

        Entity targetEntity;

        if((event.getOld().contains(InputCommand.SNEAK) && !event.getCurrent().contains(InputCommand.SNEAK))){
            //remove target
            targetEntity = null;
        }else{
            //search target

            Optional<HitResult> result = RayTraceHelper.rayTrace(player.level(), player, player.getEyePosition(1.0f) , player.getLookAngle(), 40,40, (e)->true);
            Optional<Entity> foundEntity = result
                    .filter(r->r.getType() == HitResult.Type.ENTITY)
                    .filter(r->{
                        EntityHitResult er = (EntityHitResult)r;
                        Entity target = ((EntityHitResult) r).getEntity();

                        if(target instanceof PartEntity){
                            target = ((PartEntity) target).getParent();
                        }

                        boolean isMatch = true;

                        if(target instanceof LivingEntity)
                            isMatch = TargetSelector.lockon_focus.test(player.level(), player, (LivingEntity)target);

                        if(target instanceof IShootable)
                            isMatch = false;

                        return isMatch;
                    }).map(r->((EntityHitResult) r).getEntity());

            if(!foundEntity.isPresent()){
                List<LivingEntity> entities = player.level().getNearbyEntities(
                        LivingEntity.class,
                        TargetSelector.lockon,
                        player,
                        player.getBoundingBox().inflate(12.0D, 6.0D, 12.0D));

                foundEntity = entities.stream().map(s->(Entity)s).min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
            }

            targetEntity = foundEntity
                    .map(e-> (e instanceof PartEntity) ? ((PartEntity) e).getParent() : e)
                    .orElse(null);

        }

        SBData.get(stack, ItemSlashBlade.BLADESTATE).ifPresent(s -> {
            s.setTargetEntityId(targetEntity);
        });

    }

}
