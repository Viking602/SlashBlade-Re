package mods.flammpfeil.slashblade.verification;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.BladeArmPose;
import mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only validation uses the real baked models and enum callback, with no live inventory edits. */
public final class PlayerAnimationClientProbe {
    private static final boolean ENABLED = CombatClientProbe.ENABLED;
    private static final Set<String> extracted = new HashSet<>(), applied = new HashSet<>();
    private static boolean runningSuite;
    private static int autoPass;
    public static void register() {
        if (!ENABLED) return;
        PlayerRenderClientProbe.register();
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("sbverify").then(Commands.literal("animations").executes(context -> verify()))));
        if (Boolean.getBoolean("slashblade.animationAutoVerify")) {
            NeoForge.EVENT_BUS.addListener((ClientResourceLoadFinishedEvent event) -> Minecraft.getInstance().execute(() -> {
                int result = verify();
                if (result == 1 && autoPass++ == 0) Minecraft.getInstance().reloadResourcePacks();
                else if (result == 1 && PlayerRenderClientProbe.ENABLED) PlayerRenderClientProbe.start();
                else if (Boolean.getBoolean("slashblade.animationAutoExit")) Minecraft.getInstance().stop();
            }));
        }
    }
    public static void extracted(LivingEntity entity, BladeAnimationTimeline timeline, PlayerBladeAnimation.Pose pose) {
        if (!ENABLED) return;
        String key = entity.getUUID() + ":" + timeline.combo().getName();
        if (extracted.add(key)) SlashBlade.LOGGER.info("Player animation extracted: entity={} local={} combo={} frame={} legBlend={}",
                entity.getId(), entity == Minecraft.getInstance().player, timeline.combo().getName(), timeline.frame(), pose.blendLegs);
    }
    public static void applied(HumanoidRenderState state, HumanoidModel<?> model) {
        if (!ENABLED || runningSuite || !(state instanceof AvatarRenderState avatar)) return;
        String key = avatar.id + ":" + model.getClass().getName();
        if (applied.add(key)) SlashBlade.LOGGER.info("Player animation applied to model: entity={} model={} rightArm={},{},{} leftArm={},{},{}",
                avatar.id, model.getClass().getSimpleName(), model.rightArm.xRot, model.rightArm.yRot, model.rightArm.zRot,
                model.leftArm.xRot, model.leftArm.yRot, model.leftArm.zRot);
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Player animation verification failed: " + description);
    }
    private static int verify() {
        runningSuite = true;
        try {
            var mc = Minecraft.getInstance();
            int clips = 0, cases = 0;
            var clipNames = new ArrayList<String>();
            var amplitudes = new LinkedHashMap<String, Float>();
            for (boolean slim : new boolean[] {false, true}) {
                var bodyModel = new PlayerModel(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                var armor = ArmorModelSet.bake(slim ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR,
                        mc.getEntityModels(), part -> new PlayerModel(part, slim));
                var models = java.util.List.of(bodyModel, armor.head(), armor.chest(), armor.legs(), armor.feet());
                for (var combo : ComboState.NONE.getRegistry().values()) {
                    if (!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || combo == ComboState.NONE
                            || combo == Extra.STANDBY_EX || combo == Extra.STANDBY_INAIR) continue;
                    if (!slim) { clips++; clipNames.add(combo.getName()); }
                    for (float progress : new float[] {0.0F, 0.25F, 0.5F, 0.75F, 1.0F}) {
                        float frame = combo.getStartFrame() + (combo.getEndFrame() - combo.getStartFrame()) * progress;
                        var pose = PlayerBladeAnimation.sample(new BladeAnimationTimeline(combo, frame));
                        require(pose != null, "missing clip " + combo.getName());
                        if (!slim) {
                            float amplitude = 0;
                            for (int bone = 1; bone < 7; bone++) for (int axis = 3; axis < 6; axis++)
                                amplitude = Math.max(amplitude, Math.abs(pose.component(bone, axis)));
                            amplitudes.merge(combo.getName(), amplitude, Math::max);
                        }
                        for (int bone = 0; bone < 7; bone++) for (int axis = 0; axis < 6; axis++)
                            require(Float.isFinite(pose.component(bone, axis)), "nonfinite bone " + combo.getName());
                        // Sample another entity before submitting this one: snapshots must remain independent.
                        float stored = pose.component(4, 3);
                        PlayerBladeAnimation.sample(new BladeAnimationTimeline(Extra.EX_VOID_SLASH, 2238));
                        require(pose.component(4, 3) == stored, "snapshot overwritten by another player");
                        for (var model : models) {
                        for (var mainArm : HumanoidArm.values()) {
                            var state = new AvatarRenderState();
                            state.mainArm = mainArm;
                            state.skin = net.minecraft.client.resources.DefaultPlayerSkin.get(new java.util.UUID(0, slim ? 0 : 9));
                            state.setRenderData(PlayerBladeAnimation.POSE, pose);
                            if (mainArm == HumanoidArm.RIGHT) state.rightArmPose = BladeArmPose.BLADE.getValue();
                            else state.leftArmPose = BladeArmPose.BLADE.getValue();
                            model.setupAnim(state);
                            BladeRigClientProbe.verifyModel(model, state);
                            cases++;
                        }
                        }
                    }
                }
                var model = bodyModel;
                var idle = new AvatarRenderState();
                model.setupAnim(idle);
                require(model.rightArm.xRot == 0 && model.leftArm.xRot == 0, "switching away retained the previous attack pose");
                require(model.root().x == 0 && model.root().y == 0 && model.root().z == 0
                        && model.root().xRot == 0 && model.root().yRot == 0 && model.root().zRot == 0,
                        "switching away retained the previous whole-body pose");
            }
            require(PlayerBladeAnimation.sample(new BladeAnimationTimeline(Extra.STANDBY_EX, 0)) == null, "standby replaces vanilla movement");
            require(!PlayerBladeAnimation.sample(new BladeAnimationTimeline(Extra.EX_SUPER_SA, 1920)).blendLegs, "super SA lacks whole-body pose");
            require(PlayerBladeAnimation.sample(new BladeAnimationTimeline(Extra.EX_COMBO_A1, 5)).blendLegs, "ground combo suppresses walking legs");
            require(amplitudes.getOrDefault(Extra.EX_COMBO_A1.getName(), 0F) > .15F, "left/right attack animation is a static pose");
            require(amplitudes.getOrDefault(Extra.EX_SUPER_SA.getName(), 0F) > .15F, "super SA animation is a static pose");
            var language = net.minecraft.locale.Language.getInstance();
            Map<String, String> chinese = new LinkedHashMap<>();
            try (var reader = mc.getResourceManager().openAsReader(SlashBlade.id("lang/zh_cn.json"))) {
                var values = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (var entry : values.entrySet()) chinese.put(entry.getKey(), entry.getValue().getAsString());
            }
            try (var reader = mc.getResourceManager().openAsReader(SlashBlade.id("lang/en_us.json"))) {
                var english = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                require(chinese.size() >= 146 && chinese.keySet().containsAll(english.keySet()), "incomplete Chinese language file");
            }
            if ("zh_cn".equals(mc.options.languageCode)) {
                for (var entry : chinese.entrySet()) require(language.getOrDefault(entry.getKey()).equals(entry.getValue()),
                        "Chinese locale did not load " + entry.getKey());
                require(net.minecraft.network.chat.Component.translatable("slashblade.tooltip.killcount", 123).getString().equals("斩杀数：123"),
                        "Chinese tooltip placeholder does not format");
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", "passed"); report.put("pass", autoPass); report.put("clips", clips); report.put("clipNames", clipNames);
            report.put("modelCases", cases); report.put("models", "standard/slim body + 4 armor slots, right/left hands");
            report.put("maximumPoseAngleRadians", amplitudes);
            report.put("chineseKeys", chinese.size()); report.put("activeLanguage", mc.options.languageCode);
            report.put("chineseFontWidth", mc.font.width("拔刀剑"));
            report.put("transitions", BladeMotionClientProbe.verifyTransitions());
            report.put("rigBinding", BladeRigClientProbe.verify());
            report.put("articulatedRig", ArticulatedRigClientProbe.verify());
            report.put("katanaChoreography", KatanaChoreographyClientProbe.verify());
            report.put("scope", "real client baked model setupAnim callback; no UI automation or simulated key input");
            if (mc.level != null && mc.getConnection() != null) report.put("icons", BladeIconClientProbe.verify());
            String reportPath = System.getProperty("slashblade.animationReport");
            if (reportPath != null) java.nio.file.Files.writeString(java.nio.file.Path.of(reportPath + "-" + autoPass + ".json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report), java.nio.charset.StandardCharsets.UTF_8);
            SlashBlade.LOGGER.info("Player animation verification PASSED: clips={} modelCases={} ChineseKeys={} language={} standard/slim body and armor right/left hands, finite bones, immutable snapshots, reset and leg blending",
                    clips, cases, chinese.size(), mc.options.languageCode);
            return 1;
        } catch (Exception error) {
            SlashBlade.LOGGER.error("Player animation verification FAILED", error);
            return 0;
        } finally { runningSuite = false; }
    }

    private PlayerAnimationClientProbe() {}
}
