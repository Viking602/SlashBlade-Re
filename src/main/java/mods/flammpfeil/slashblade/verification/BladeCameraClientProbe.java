package mods.flammpfeil.slashblade.verification;

import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.client.animation.BladeMotionState;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Resharped animates the weapon without adding attack rotations to the world camera. */
public final class BladeCameraClientProbe {
    public static Map<String,Object> verify() {
        var mc=Minecraft.getInstance();var player=mc.player;
        var original=player.getMainHandItem();var view=mc.options.getCameraType();var hand=player.getMainArm();
        float yaw=player.getYRot(),pitch=player.getXRot();int samples=0;
        try {
            var sword=new ItemStack(SBItems.slashblade);
            var blade=SBData.get(sword,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            for(var camera:CameraType.values()) for(var arm:HumanoidArm.values()) {
                mc.options.setCameraType(camera);player.setMainArm(arm);
                for(var combo:ComboState.NONE.getRegistry().values()) {
                    blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime());BladeMotionState.clear();
                    for(int frame=0;frame<=16;frame++) {
                        var event=new ViewportEvent.ComputeCameraAngles(mc.gameRenderer.getMainCamera(),frame/2.0,137,22,1.5F);
                        NeoForge.EVENT_BUS.post(event);
                        if(event.getYaw()!=137 || event.getPitch()!=22 || event.getRoll()!=1.5F)
                            throw new IllegalStateException("Attack changed native camera: "+combo.getName()+" "+camera);
                        samples++;
                    }
                }
            }
            if(player.getYRot()!=yaw || player.getXRot()!=pitch)throw new IllegalStateException("Camera rendering changed gameplay aim");
            return Map.of("status","passed","samples",samples,"scope","all registered clips, both hands, three views; preserves supplied camera angles and player aim");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND,original);player.setMainArm(hand);mc.options.setCameraType(view);BladeMotionState.clear();
        }
    }
    private BladeCameraClientProbe() {}
}
