package mods.flammpfeil.slashblade.verification;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.client.animation.BladeMotionState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.Files;
import java.nio.file.Path;

/** Direct framebuffer captures in the isolated mod test, with no mouse/keyboard or UI automation. */
public final class BladeVisualClientProbe {
    public static final boolean ENABLED = PlayerRenderClientProbe.ENABLED && Boolean.getBoolean("slashblade.animationScreenshots");
    private record Scene(String name, CameraType camera, ComboState combo, int elapsedTicks, float pitch) {}
    private static final Scene[] allScenes = {
            new Scene("third-idle", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0),
            new Scene("side-idle", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0),
            new Scene("third-combo-a1", CameraType.THIRD_PERSON_FRONT, Extra.EX_COMBO_A1, 5, 0),
            new Scene("third-combo-a2", CameraType.THIRD_PERSON_FRONT, Extra.EX_COMBO_A2, 5, 0),
            new Scene("third-just-sa", CameraType.THIRD_PERSON_FRONT, Extra.EX_JUDGEMENT_CUT_SLASH_JUST, 5, 0),
            new Scene("third-super-sa", CameraType.THIRD_PERSON_FRONT, Extra.EX_SUPER_SA, 9, 0),
            new Scene("first-level", CameraType.FIRST_PERSON, Extra.STANDBY_EX, 0, 0),
            new Scene("first-up", CameraType.FIRST_PERSON, Extra.STANDBY_EX, 0, -65),
            new Scene("first-down", CameraType.FIRST_PERSON, Extra.STANDBY_EX, 0, 65),
            new Scene("first-combo-a1", CameraType.FIRST_PERSON, Extra.EX_COMBO_A1, 5, 0),
            new Scene("third-left", CameraType.THIRD_PERSON_FRONT, Extra.EX_COMBO_A2, 5, 0),
            new Scene("first-left", CameraType.FIRST_PERSON, Extra.EX_COMBO_A1, 5, 0),
            new Scene("third-armor", CameraType.THIRD_PERSON_FRONT, Extra.EX_COMBO_A1, 5, 0),
            new Scene("sequence-third", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0),
            new Scene("sequence-first", CameraType.FIRST_PERSON, Extra.STANDBY_EX, 0, 0),
            new Scene("sequence-draw-sheath", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0),
            new Scene("sequence-draw-sheath-first", CameraType.FIRST_PERSON, Extra.STANDBY_EX, 0, 0)
            ,new Scene("sequence-draw-sheath-side", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0)
            ,new Scene("sequence-draw-sheath-oblique", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0)
            ,new Scene("sequence-interrupted-draw", CameraType.THIRD_PERSON_FRONT, Extra.STANDBY_EX, 0, 0)
    };
    private static final Scene[] scenes=Boolean.getBoolean("slashblade.bladeSizeComparison") ? sizeScenes()
            : Boolean.getBoolean("slashblade.firstPersonFraming") ? framingScenes()
            : Boolean.getBoolean("slashblade.perspectiveComparison")
            ? new Scene[]{new Scene("sequence-third",CameraType.THIRD_PERSON_FRONT,Extra.STANDBY_EX,0,20),
                          new Scene("sequence-first",CameraType.FIRST_PERSON,Extra.STANDBY_EX,0,20)} : allScenes;
    private static Scene[] sizeScenes() {
        var result=new java.util.ArrayList<Scene>();
        for(String variant:new String[]{"intact","broken"}) {
            result.add(new Scene("size-"+variant+"-third-cut",CameraType.THIRD_PERSON_FRONT,Extra.EX_COMBO_A1,5,0));
            result.add(new Scene("size-"+variant+"-third-idle",CameraType.THIRD_PERSON_FRONT,Extra.STANDBY_EX,0,0));
            result.add(new Scene("size-"+variant+"-first",CameraType.FIRST_PERSON,Extra.EX_COMBO_A1,3,0));
        }
        return result.toArray(Scene[]::new);
    }
    private static Scene[] framingScenes() {
        var result=new java.util.ArrayList<Scene>();
        for(int pitch:new int[]{0,45,65,80,89,-65}) {
            result.add(new Scene("framing-"+pitch+"-idle",CameraType.FIRST_PERSON,Extra.STANDBY_EX,0,pitch));
            result.add(new Scene("framing-"+pitch+"-draw",CameraType.FIRST_PERSON,Extra.EX_COMBO_A1,3,pitch));
            result.add(new Scene("framing-"+pitch+"-cut",CameraType.FIRST_PERSON,Extra.EX_COMBO_A3,5,pitch));
            result.add(new Scene("framing-"+pitch+"-sa",CameraType.FIRST_PERSON,Extra.EX_JUDGEMENT_CUT_SLASH,5,pitch));
        }
        result.add(new Scene("framing-third-idle",CameraType.THIRD_PERSON_FRONT,Extra.STANDBY_EX,0,0));
        result.add(new Scene("framing-third-cut",CameraType.THIRD_PERSON_FRONT,Extra.EX_COMBO_A3,5,0));
        return result.toArray(Scene[]::new);
    }
    private static boolean started, pending;
    private static int sceneIndex, frames, sequenceFrame;
    private static BladeMotionState.Sample scriptedSample;
    private static BladeMotionState.History sequenceHistory;
    public static BladeMotionState.Sample sampleOverride(net.minecraft.world.entity.LivingEntity entity) {
        return started && entity == Minecraft.getInstance().player ? scriptedSample : null;
    }
    public static void lockComparisonState(net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
        if(!started || !Boolean.getBoolean("slashblade.perspectiveComparison") || entity!=Minecraft.getInstance().player) return;
        // Both camera passes sample the same authored time AND vanilla idle clock.
        // This opt-in comparison ends before the real integrated-server recording.
        state.ageInTicks=sequenceFrame*(20F/30);state.walkAnimationPos=0;state.walkAnimationSpeed=0;
    }
    private static ItemStack sword;
    private static double x, y, z;
    private static net.minecraft.world.entity.decoration.ArmorStand cameraAnchor;

    public static void register() {
        if (!ENABLED) return;
        CombatShowcaseClientProbe.register();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) -> {
            if (started) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> {
            var mc = Minecraft.getInstance();
            if (!started || pending || sceneIndex >= scenes.length || mc.player == null || mc.level == null || mc.screen != null) return;
            Scene scene = scenes[sceneIndex];
            mc.options.setCameraType(scene.camera());
            mc.options.hideGui = false;
            boolean armor = scene.name().equals("third-armor");
            mc.player.setItemSlot(EquipmentSlot.CHEST, armor ? new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE) : ItemStack.EMPTY);
            mc.player.setItemSlot(EquipmentSlot.LEGS, armor ? new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS) : ItemStack.EMPTY);
            mc.player.setPos(x, y, z); mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setOnGround(true);
            mc.player.setXRot(scene.pitch()); mc.player.xRotO = scene.pitch();
            mc.player.setYRot(0); mc.player.yRotO = 0;
            mc.player.yHeadRot = mc.player.yHeadRotO = mc.player.yBodyRot = mc.player.yBodyRotO = 0;
            boolean comparisonThird=Boolean.getBoolean("slashblade.perspectiveComparison") && scene.camera()!=CameraType.FIRST_PERSON;
            if(scene.name().contains("side") || scene.name().contains("oblique") || comparisonThird) {
                if(cameraAnchor==null) cameraAnchor=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,x,y,z);
                cameraAnchor.setPos(x,y,z); cameraAnchor.setYRot(comparisonThird ? -25 : scene.name().contains("side") ? -90 : -45);
                cameraAnchor.setXRot(comparisonThird ? -8 : 0); cameraAnchor.setOldPosAndRot();
                cameraAnchor.yHeadRot=cameraAnchor.yHeadRotO=cameraAnchor.yBodyRot=cameraAnchor.yBodyRotO=cameraAnchor.getYRot();
                mc.setCameraEntity(cameraAnchor);
            } else mc.setCameraEntity(mc.player);
            mc.player.setMainArm(scene.name().endsWith("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            var blade = SBData.get(sword, ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            if(Boolean.getBoolean("slashblade.bladeSizeComparison")) {
                blade.setModel(SlashBlade.id("model/named/agito.obj"));
                blade.setTexture(SlashBlade.id("model/named/a_tukumo.png"));
                blade.setDamage(scene.name().contains("broken")?1:0);
                blade.setBroken(scene.name().contains("broken"));
            }
            blade.setComboSeq(scene.combo()); blade.setLastActionTime(mc.level.getGameTime() - scene.elapsedTicks());
            if (scene.name().startsWith("sequence")) {
                int[] starts = {0, 20, 36, 52, 74, 112, 188, 220};
                ComboState[] clips = {Extra.STANDBY_EX, Extra.EX_COMBO_A1, Extra.EX_COMBO_A2, Extra.EX_COMBO_A3,
                        Extra.EX_COMBO_C, Extra.EX_SUPER_SA, Extra.EX_JUDGEMENT_CUT_SLASH_JUST, Extra.STANDBY_EX};
                if (scene.name().contains("draw-sheath")) {
                    starts = new int[]{0, 20, 100, 136, 200};
                    clips = new ComboState[]{Extra.STANDBY_EX, Extra.EX_COMBO_A1, Extra.EX_COMBO_A1, Extra.EX_COMBO_A2, Extra.STANDBY_EX};
                }
                if(scene.name().contains("interrupted-draw")) {
                    starts=new int[]{0,20,24,80,112,160,164,200};
                    clips=new ComboState[]{Extra.STANDBY_EX,Extra.EX_COMBO_A1,Extra.EX_COMBO_A2,Extra.EX_COMBO_A1,
                            Extra.EX_COMBO_A2,Extra.EX_COMBO_A1,Extra.EX_COMBO_A3,Extra.STANDBY_EX};
                }
                int stage = 0;
                while (stage + 1 < starts.length && sequenceFrame >= starts[stage + 1]) stage++;
                if (sequenceHistory == null) sequenceHistory = new BladeMotionState.History();
                double time = 1000 + sequenceFrame * (20.0 / 30);
                blade.setComboSeq(clips[stage]); blade.setLastActionTime(1000 + (long)(starts[stage] * (20.0 / 30)));
                scriptedSample = sequenceHistory.resolve(blade.getUniqueId(), stage, time,
                        BladeAnimationTimeline.resolve(blade, (long)time, (float)(time - (long)time)));
            } else {
                scriptedSample = BladeMotionState.Sample.direct(BladeAnimationTimeline.resolve(blade, mc.level.getGameTime(), .5F));
            }
            frames++;
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> {
            var mc = Minecraft.getInstance();
            if (!started || pending || sceneIndex >= scenes.length || mc.screen != null || frames < (Boolean.getBoolean("slashblade.firstPersonFraming") ? 3 : 30)) return;
            pending = true;
            Scene scene = scenes[sceneIndex];
            boolean sequence = scene.name().startsWith("sequence");
            Path output = mc.gameDirectory.toPath().resolve("screenshots").resolve(sequence
                    ? scene.name() + "/" + String.format("%03d.png", sequenceFrame) : scene.name() + ".png");
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                try (image) {
                    Files.createDirectories(output.getParent()); image.writeToFile(output);
                    SlashBlade.LOGGER.info("Blade visual sample saved: {} combo={} elapsedTicks={} camera={} pitch={}",
                            output, scene.combo().getName(), scene.elapsedTicks(), scene.camera(), scene.pitch());
                } catch (Exception error) { SlashBlade.LOGGER.error("Could not save blade visual sample", error); }
                mc.execute(() -> {
                    if (!sequence || ++sequenceFrame >= 240) {
                        sceneIndex++; sequenceFrame = 0; sequenceHistory = null;
                    }
                    frames = sequence ? 29 : 0; pending = false;
                    if (sceneIndex == scenes.length) {
                        started=false; scriptedSample=null; mc.setCameraEntity(mc.player);
                        if(Boolean.getBoolean("slashblade.recipeShowcase")) BladeRecipeClientProbe.start();
                        else if(Boolean.getBoolean("slashblade.firstPersonFraming")) mc.stop();
                        else CombatShowcaseClientProbe.start();
                    }
                });
            });
        });
    }

    public static void start() {
        if(!Boolean.getBoolean("slashblade.firstPersonFraming") && (Boolean.getBoolean("slashblade.combatShowcase") ||
                Boolean.getBoolean("slashblade.combatFirstPerson") && !Boolean.getBoolean("slashblade.perspectiveComparison"))) {
            CombatShowcaseClientProbe.start();
            return;
        }
        var mc = Minecraft.getInstance();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            mc.player.setItemSlot(slot, ItemStack.EMPTY);
        sword = new ItemStack(SBItems.slashblade);
        x = mc.player.getX(); z = mc.player.getZ();
        y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
        started = true;
    }
    private BladeVisualClientProbe() {}
}
