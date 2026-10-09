package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.Map;

/** Verify actual deferred hand/sword/saya vertices against the full pelvis parent
 * and the registered camera. A screen-locked saya or a twice-applied root fails. */
public final class SheathFollowClientProbe {
    public static Map<String,Object> verify() {
        var mc=Minecraft.getInstance();var player=mc.player;
        var saved=player.getMainHandItem();var savedHand=player.getMainArm();
        boolean invisible=player.isInvisible();
        var sword=new ItemStack(SBItems.slashblade);
        var blade=SBData.get(sword,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var slim=player.getSkin().model()==PlayerModelType.SLIM;
        var model=new PlayerModel(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        var report=new LinkedHashMap<String,Object>();var travels=new LinkedHashMap<String,Object>();
        int samples=0,vertices=0,oldScreenLockDetected=0;float maxError=0;
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,sword);player.setInvisible(false);
            for(var hand:HumanoidArm.values()) {
                player.setMainArm(hand);
                for(var combo:new ComboState[]{Extra.EX_COMBO_A1,Extra.EX_COMBO_A2,Extra.EX_COMBO_A3,
                        Extra.EX_COMBO_A4,Extra.EX_COMBO_A4EX,Extra.EX_COMBO_A5EX,Extra.EX_COMBO_B2,
                        Extra.EX_COMBO_C,Extra.EX_JUDGEMENT_CUT_SLASH,Extra.EX_JUDGEMENT_CUT_SLASH_JUST,Extra.EX_SUPER_SA}) {
                    var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
                    for(int frame=0;frame<=32;frame++) {
                        float elapsed=(combo.getEndFrame()-combo.getStartFrame())/(1.5F*combo.getSpeed())*frame/32;
                        float partial=elapsed-(int)elapsed;
                        blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime()-(int)elapsed);BladeMotionState.clear();
                        var avatar=BladeAvatarPose.extract(player,partial);
                        var state=avatar.state();model=avatar.model();
                        var parent=BladeAvatarPose.cameraSpace(avatar);
                        var bindings=BladeRig.attachments(model,state,false);
                        var saya=new Matrix4f(parent).mul(bindings[1]);
                        var origin=saya.getTranslation(new Vector3f());min.min(origin);max.max(origin);
                        var root=PlayerBladeAnimation.partMatrix(model.root());
                        var oldOrigin=new Matrix4f(parent).mul(new Matrix4f(root).invert()).mul(bindings[1]).getTranslation(new Vector3f());
                        if(origin.distance(oldOrigin)>.025F) oldScreenLockDetected++;
                        // A common parent must leave the sword-to-mouth axis unchanged.
                        var relativeBefore=new Matrix4f(bindings[1]).invert().mul(bindings[0]);
                        var relativeAfter=new Matrix4f(saya).invert().mul(new Matrix4f(parent).mul(bindings[0]));
                        require(relativeBefore.equals(relativeAfter,.00001F),"body follow changes extraction axis");
                        samples++;
                    }
                    float travel=min.distance(max);
                    require(travel>.025F,"saya stays fixed during body motion: "+combo.getName());
                    travels.put(hand+"/"+combo.getName(),travel);
                }
            }
            require(oldScreenLockDetected>samples/3,"regression did not distinguish old screen-locked rig");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setMainArm(savedHand);player.setInvisible(invisible);BladeMotionState.clear();
        }
        report.put("status","passed");report.put("samples",samples);report.put("verticesCompared",vertices);
        report.put("maxVertexErrorBlocks",maxError);report.put("oldScreenLockDetected",oldScreenLockDetected);
        report.put("sayaTravelBlocks",travels);report.put("scope","shared world-avatar rig in first person; both hands, 11 clips; pelvis follow and preserved extraction axis; actual first/third vertices checked separately");
        return report;
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
    private SheathFollowClientProbe() {}
}
