package mods.flammpfeil.slashblade.verification;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation;
import mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Disabled by default. Real entity-renderer integration in a marked, isolated test game directory. */
public final class PlayerRenderClientProbe {
    public static final boolean ENABLED = CombatClientProbe.ENABLED && Boolean.getBoolean("slashblade.animationWorldVerify");
    private static boolean started, finished;
    private static int ticks;

    public static void register() {
        if (!ENABLED) return;
        BladeVisualClientProbe.register();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (!started || finished) return;
            var mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.player.tickCount >= 20) {
                finished = true;
                if(!verify()) { mc.stop(); return; }
                if (BladeVisualClientProbe.ENABLED) BladeVisualClientProbe.start();
                else mc.stop();
            } else if (++ticks > 2400) {
                finished = true;
                SlashBlade.LOGGER.error("Player renderer world verification timed out");
                mc.stop();
            }
        });
    }

    public static void start() {
        if (started) return;
        started = true;
        var mc = Minecraft.getInstance();
        Path gameDirectory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        String permitted = System.getProperty("slashblade.worldVerificationDirectory", "");
        if (!gameDirectory.equals(Path.of(permitted).toAbsolutePath().normalize())
                || !Files.isRegularFile(gameDirectory.resolve("SLASHBLADE_AUTOTEST_ONLY"))) {
            SlashBlade.LOGGER.error("Refusing world verification outside the marked test fixture");
            finished = true; mc.stop(); return;
        }
        var settings = new LevelSettings("SlashBlade renderer verification", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("slashblade-renderer-" + System.currentTimeMillis(), settings,
                new WorldOptions(602, false, false), WorldPresets::createFlatWorldDimensions, mc.screen);
    }

    @SuppressWarnings("unchecked")
    private static boolean verify() {
        var mc = Minecraft.getInstance();
        var report = new LinkedHashMap<String, Object>();
        try {
            require(UserPoseOverrider.UsePoseOverrider, "whole-body render hook is not registered");
            int cases = 0;
            for (var type : PlayerModelType.values()) {
                var renderer = (AvatarRenderer<AbstractClientPlayer>) mc.getEntityRenderDispatcher().getPlayerRenderers().get(type);
                require(renderer != null, "missing player renderer " + type);
                for (boolean local : new boolean[]{true, false}) {
                    AbstractClientPlayer actor = local ? mc.player : new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "BladeFixture"));
                    if (!local) actor.setPos(mc.player.position());
                    actor.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    actor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    actor.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                    actor.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    for (var mainArm : HumanoidArm.values()) {
                        actor.setMainArm(mainArm);
                        var sword = new ItemStack(SBItems.slashblade);
                        actor.setItemSlot(EquipmentSlot.MAINHAND, sword);
                        var blade = SBData.get(sword, ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
                        for (var combo : ComboState.NONE.getRegistry().values()) {
                            if (!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || combo == ComboState.NONE
                                    || combo == Extra.STANDBY_EX || combo == Extra.STANDBY_INAIR) continue;
                            blade.setComboSeq(combo);
                            blade.setLastActionTime(mc.level.getGameTime());
                            // These are independent clips. Transition continuity is
                            // tested separately with a deliberately advancing clock.
                            mods.flammpfeil.slashblade.client.animation.BladeMotionState.clear();
                            AvatarRenderState state = renderer.createRenderState(actor, .5F);
                            var pose = state.getRenderData(PlayerBladeAnimation.POSE);
                            require(pose != null, "registered entity modifier omitted " + combo.getName());
                            require(state.getRenderData(PlayerBladeAnimation.BODY_TRANSFORM) != null, "whole-body pose was not extracted");
                            require(state.getRenderData(LivingBladeLayer.GEOMETRY) != null, "carried sword animation was not extracted");
                            var nodes = new SubmitNodeStorage();
                            var matrix = new PoseStack();
                            renderer.submit(state, matrix, nodes, new CameraRenderState());
                            var model = renderer.getModel();
                            BladeRigClientProbe.verifyModel(model, state);
                            require(matrix.last().pose().isFinite(), "whole-body render matrix is not finite");
                            cases++;
                        }
                        actor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        var idle = renderer.createRenderState(actor, .5F);
                        require(idle.getRenderData(PlayerBladeAnimation.POSE) == null, "animation persisted after switching away");
                    }
                }
            }
            report.put("status", "passed"); report.put("entityRendererCases", cases);
            report.put("scope", "real standard/slim AvatarRenderer extraction and submit with registered NeoForge modifiers, local/remote players, both hands, armor equipped; no UI automation");
            report.put("icons", BladeIconClientProbe.verify());
            report.put("notoGeometry", NotoGeometryClientProbe.verify());
            report.put("cameraAndSwordTransforms", BladeMotionClientProbe.verifyFirstPerson());
            report.put("firstPersonFraming", FirstPersonFramingClientProbe.verify());
            report.put("cameraBodyFollow", BladeCameraClientProbe.verify());
            report.put("sheathBodyFollow", SheathFollowClientProbe.verify());
            report.put("perspectiveParity", PerspectiveParityClientProbe.verify(true));
            report.put("worldRigParity", PerspectiveParityClientProbe.verifyRegisteredRig());
            SlashBlade.LOGGER.info("Player entity renderer verification PASSED: cases={} local/remote standard/slim both hands with armor", cases);
        } catch (Throwable error) {
            report.put("status", "failed"); report.put("error", error.toString());
            SlashBlade.LOGGER.error("Player entity renderer verification FAILED", error);
        }
        try {
            String prefix = System.getProperty("slashblade.animationReport");
            if (prefix != null) Files.writeString(Path.of(prefix + "-world.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
        } catch (Exception error) { SlashBlade.LOGGER.error("Could not save renderer verification report", error); }
        return "passed".equals(report.get("status"));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private PlayerRenderClientProbe() {}
}
