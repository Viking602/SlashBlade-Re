package mods.flammpfeil.slashblade.client;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.RayTraceHelper;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
public final class LockOnClient {
    public static void onEntityUpdate(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {

        if(Minecraft.getInstance().player == null) return;

        LocalPlayer player = Minecraft.getInstance().player;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ItemSlashBlade)) return;

        SBData.get(stack, ItemSlashBlade.BLADESTATE).ifPresent(s -> {

            Entity target = s.getTargetEntity(player.level());

            if (target == null) return;
            if(!target.isAlive()) return;

            LivingEntity entity = player;

            if(!entity.level().isClientSide()) return;
            if(!SBData.get(entity, CapabilityInputState.INPUT_STATE).filter(input->input.getCommands().contains(InputCommand.SNEAK)).isPresent()) return;


            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

            float oldYawHead = entity.yHeadRot;
            float oldYawOffset = entity.yBodyRot;
            float oldPitch = entity.getXRot();
            float oldYaw = entity.getYRot();

            float prevYawHead = entity.yHeadRotO;
            float prevYawOffset = entity.yBodyRotO;
            float prevYaw = entity.yRotO;
            float prevPitch = entity.xRotO;

            entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0,target.getEyeHeight() / 2.0,0));

            float step = 0.125f * partialTicks;

            step *= Math.min(1.0f ,Math.abs(Mth.wrapDegrees(oldYaw - entity.yHeadRot) * 0.5));

            entity.setXRot(Mth.rotLerp(step,oldPitch ,entity.getXRot()));
            entity.setYRot(Mth.rotLerp(step, oldYaw , entity.getYRot()));
            entity.setYHeadRot(Mth.rotLerp(step, oldYawHead , entity.getYHeadRot()));

            entity.yBodyRot = oldYawOffset;

            entity.yBodyRotO = prevYawOffset;
            entity.yHeadRotO = prevYawHead;
            entity.yRotO = prevYaw;
            entity.xRotO = prevPitch;
        });
    }

}
