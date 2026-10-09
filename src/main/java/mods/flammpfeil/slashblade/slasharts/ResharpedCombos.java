package mods.flammpfeil.slashblade.slasharts;
import java.util.*;
import java.util.function.*;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.event.FallHandler;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
/** SA frames, timings and hit sequences adapted from Resharped 1.9.65. */
public final class ResharpedCombos {
    private static final Map<String,ComboState> STATES=new LinkedHashMap<>();
    public static ComboState get(String id) {
        if(id.equals("none")) return ComboState.NONE;
        if(id.equals("judgement_cut_sheath")) return mods.flammpfeil.slashblade.capability.slashblade.combo.Extra.EX_JUDGEMENT_CUT_SHEATH;
        var value=STATES.get(id);
        if(value==null) throw new IllegalArgumentException("Unknown SA combo: "+id);
        return value;
    }
    public static void bootstrap() {}
    public static ComboState enterTimedSegment(LivingEntity user,mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state,ComboState combo) {
        if(user.level().isClientSide() || combo==state.getComboSeq())return combo;
        String key=state.getUniqueId()+":"+state.getLastActionTime()+":"+combo.getName();
        var tag=user.getPersistentData();
        if(!tag.getStringOr("slashblade:timed_sa_segment","").equals(key)) {
            tag.putString("slashblade:timed_sa_segment",key);
            var event=new mods.flammpfeil.slashblade.event.SlashBladeEvent.NextOfTimeOutComboEvent(
                    user.getMainHandItem(),state,user,mods.flammpfeil.slashblade.SlashBlade.id(combo.getName()));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            var next=ComboState.NONE.valueOf(event.getNextCombo().getPath());
            if(next!=null && next!=combo) {
                state.updateComboSeq(user,next);
                return next;
            }
            // Old combo tracks perform their hits in tickAction. Only the source
            // SA stages have entry attacks that must run on timeout as well.
            if(combo.getName().startsWith("resharped_"))combo.clickAction(user);
        }
        return combo;
    }
    private static ComboState register(String id,Builder b) {
        var state=new ComboState("resharped_"+id,b.priority,()->b.start,()->b.end,()->b.speed,()->false,()->0,b.motion,b.next,()->b.timeout.apply(null));
        if(b.aerial) state.setIsAerial();
        state.setClickAction(b.click).setReleaseAction(b.release);
        b.ticks.forEach(state::addTickAction);b.hits.forEach(state::addHitEffect);STATES.put(id,state);return state;
    }
    private static final class Builder {
        int start,end,priority=100;float speed=1;boolean aerial;
        Identifier motion=DefaultResources.ExMotionLocation;
        Function<LivingEntity,ComboState> next=e->ComboState.NONE,timeout=e->ComboState.NONE;
        Consumer<LivingEntity> click=e->{};
        BiFunction<LivingEntity,Integer,mods.flammpfeil.slashblade.specialattack.SlashArts.ArtsType> release=(e,t)->mods.flammpfeil.slashblade.specialattack.SlashArts.ArtsType.Fail;
        List<Consumer<LivingEntity>> ticks=new ArrayList<>();List<BiConsumer<LivingEntity,LivingEntity>> hits=new ArrayList<>();
        Builder startAndEnd(int a,int b){start=a;end=b;return this;} Builder speed(float v){speed=v;return this;} Builder priority(int p){priority=p;return this;}
        Builder aerial(){aerial=true;return this;} Builder motionLoc(Identifier v){motion=v;return this;}
        Builder next(Function<LivingEntity,ComboState> v){next=v;return this;} Builder nextOfTimeout(Function<LivingEntity,ComboState> v){timeout=v;return this;}
        Builder clickAction(Consumer<LivingEntity> v){click=v;return this;} Builder addTickAction(Consumer<LivingEntity> v){ticks.add(v);return this;}
        Builder addHitEffect(BiConsumer<LivingEntity,LivingEntity> v){hits.add(v);return this;}
        Builder releaseAction(BiFunction<LivingEntity,Integer,mods.flammpfeil.slashblade.specialattack.SlashArts.ArtsType> v){release=v;return this;}
    }
    public static final ComboState VOID_SLASH = register("void_slash", new Builder().startAndEnd(2200, 2277).priority(50).speed(1.0F)
            .next(entity -> get("void_slash"))
            .nextOfTimeout(entity -> get("void_slash_sheath"))
            .addTickAction(entity -> entity.setDeltaMovement(Vec3.ZERO))
            .addTickAction(ComboState.TimeLineTickAction.getBuilder().put(16, AttackManager::doVoidSlashAttack).build())
            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                    .put(16, (entityIn) -> UserPoseOverrider.setRot(entityIn, -36, true))
                    .put(16 + 1, (entityIn) -> UserPoseOverrider.setRot(entityIn, -36, true))
                    .put(16 + 2, (entityIn) -> UserPoseOverrider.setRot(entityIn, -36, true))
                    .put(16 + 3, (entityIn) -> UserPoseOverrider.setRot(entityIn, -36, true))
                    .put(16 + 4, (entityIn) -> UserPoseOverrider.setRot(entityIn, -36, true))
                    .put(16 + 5, (entityIn) -> UserPoseOverrider.setRot(entityIn, 0, true))
                    .put(57, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 1, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 2, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 3, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 4, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 5, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 6, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 7, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 8, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 9, (entityIn) -> UserPoseOverrider.setRot(entityIn, 18, true))
                    .put(57 + 10, (entityIn) -> UserPoseOverrider.setRot(entityIn, 0, true)).build())
            .addTickAction(FallHandler::fallDecrease)
            .addHitEffect((t, a) -> StunManager.setStun(t, 40)));

    public static final ComboState VOID_SLASH_SHEATH = register("void_slash_sheath",
            new Builder().startAndEnd(2278, 2299).priority(50)
                    .next(entity -> get("none")).nextOfTimeout(entity -> get("none"))
                    .addTickAction(FallHandler::fallDecrease)
                    .addTickAction(UserPoseOverrider::resetRot)
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build())
                    .releaseAction(ComboState::releaseActionQuickCharge));

    public static final ComboState SAKURA_END_LEFT = register("sakura_end_left",
            new Builder().startAndEnd(1816, 1859).speed(6F).priority(50)
                    .next((entity) -> get("sakura_end_right"))
                    .nextOfTimeout(entity -> get("sakura_end_right"))
                    .clickAction((entityIn) -> SakuraEnd.doSlash(entityIn, 22.5F, Vec3.ZERO, false, false, 0.5))
                    .addTickAction(UserPoseOverrider::resetRot)
                    .addHitEffect(StunManager::setStun));

    public static final ComboState SAKURA_END_RIGHT = register("sakura_end_right",
            new Builder().startAndEnd(204, 218).speed(1.1F).priority(50)
                    .next((entity) -> get("none"))
                    .nextOfTimeout(entity -> get("sakura_end_finish"))
                    .clickAction((entityIn) -> SakuraEnd.doSlash(entityIn, 180F - 22.5F, Vec3.ZERO, false, true, 0.76))
                    .addTickAction(UserPoseOverrider::resetRot)
                    .addHitEffect((t, a) -> StunManager.setStun(t, 36)));

    public static final ComboState SAKURA_END_FINISH = register("sakura_end_finish",
            new Builder().startAndEnd(218, 281).priority(50).aerial()
                    .next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("sakura_end_finish2")));
    public static final ComboState SAKURA_END_FINISH2 = register("sakura_end_finish2",
            new Builder().startAndEnd(281, 314).priority(80)
                    .next(entity -> get("none")).nextOfTimeout(entity -> get("none"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build())
                    .releaseAction(ComboState::releaseActionQuickCharge));

    public static final ComboState SAKURA_END_LEFT_AIR = register("sakura_end_left_air",
            new Builder().startAndEnd(1300, 1328).speed(3.2F).priority(50)
                    .next((entity) -> get("sakura_end_right_air"))
                    .nextOfTimeout(entity -> get("sakura_end_right_air"))
                    .clickAction((entityIn) -> SakuraEnd.doSlash(entityIn, 22.5F, Vec3.ZERO, false, false, 0.5))
                    .addTickAction(UserPoseOverrider::resetRot)
                    .addTickAction(FallHandler::fallDecrease).addHitEffect(StunManager::setStun).aerial());

    public static final ComboState SAKURA_END_RIGHT_AIR = register("sakura_end_right_air",
            new Builder().startAndEnd(1200, 1210).priority(50)
                    .next((entity) -> get("none"))
                    .nextOfTimeout(entity -> get("sakura_end_finish_air"))
                    .clickAction((entityIn) -> SakuraEnd.doSlash(entityIn, 180F - 22.5F, Vec3.ZERO, false, true, 0.76))
                    .addTickAction(UserPoseOverrider::resetRot)
                    .addTickAction(FallHandler::fallDecrease).addHitEffect(StunManager::setStun).aerial());

    public static final ComboState SAKURA_END_FINISH_AIR = register("sakura_end_finish_air",
            new Builder().startAndEnd(1210, 1231).priority(50).aerial()
                    .next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("sakura_end_finish2_air"))
                    .addTickAction(FallHandler::fallDecrease));
    public static final ComboState SAKURA_END_FINISH2_AIR = register(
            "sakura_end_finish2_air",
            new Builder().startAndEnd(1231, 1241).priority(50)
                    .next(entity -> get("none")).nextOfTimeout(entity -> get("none"))
                    .addTickAction(FallHandler::fallDecrease)
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build())
                    .releaseAction(ComboState::releaseActionQuickCharge)
                    .addTickAction(FallHandler::fallDecrease));

    public static final ComboState CIRCLE_SLASH = register("circle_slash",
            new Builder().startAndEnd(725, 743).priority(50)
                    .next(entity -> get("circle_slash"))
                    .nextOfTimeout(entity -> get("circle_slash_end"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(4, (entityIn) -> CircleSlash.doCircleSlashAttack(entityIn, 180))
                            .put(5, (entityIn) -> CircleSlash.doCircleSlashAttack(entityIn, 90))
                            .put(6, (entityIn) -> CircleSlash.doCircleSlashAttack(entityIn, 0))
                            .put(7, (entityIn) -> CircleSlash.doCircleSlashAttack(entityIn, -90)).build())
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(7 - 3, (entityIn) -> UserPoseOverrider.setRot(entityIn, 72, true))
                            .put(7 - 3 + 1, (entityIn) -> UserPoseOverrider.setRot(entityIn, 72, true))
                            .put(7 - 3 + 2, (entityIn) -> UserPoseOverrider.setRot(entityIn, 72, true))
                            .put(7 - 3 + 3, (entityIn) -> UserPoseOverrider.setRot(entityIn, 72, true))
                            .put(7 - 3 + 4, (entityIn) -> UserPoseOverrider.setRot(entityIn, 72, true))
                            .put(7 - 3 + 5, UserPoseOverrider::resetRot).build())
                    .addHitEffect(StunManager::setStun));
    public static final ComboState CIRCLE_SLASH_END = register("circle_slash_end",
            new Builder().startAndEnd(743, 764).priority(100)
                    .next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("circle_slash_end2")));
    public static final ComboState CIRCLE_SLASH_END2 = register("circle_slash_end2",
            new Builder().startAndEnd(764, 787).priority(100)
                    .next(entity -> get("none")).nextOfTimeout(entity -> get("none"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build()));

    public static final ComboState DRIVE_HORIZONTAL = register("drive_horizontal",
            new Builder().startAndEnd(400, 459).priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(ComboState.TimeoutNext.buildFromFrame(15, entity -> get("none")))
                    .nextOfTimeout(entity -> get("drive_horizontal_end"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(2, (entityIn) -> AttackManager.doSlash(entityIn, -30F, Vec3.ZERO, false, false, 0.21F))
                            .put(3, (entityIn) -> Drive.doSlash(entityIn, 0F, 10, Vec3.ZERO, false, 1.5f, 2f)).build())
                    .addHitEffect(StunManager::setStun)
                    );
    public static final ComboState DRIVE_HORIZONTAL_END = register("drive_horizontal_end",
            new Builder().startAndEnd(459, 488).priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation).next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("none"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build())
                    .releaseAction(ComboState::releaseActionQuickCharge));

    public static final ComboState DRIVE_VERTICAL = register("drive_vertical",
            new Builder()
                    .startAndEnd(1600, 1659)
                    .priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(ComboState.TimeoutNext.buildFromFrame(15, entity -> get("none")))
                    .nextOfTimeout(entity -> get("drive_vertical_end"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(2, (entityIn) -> AttackManager.doSlash(entityIn, -80F, Vec3.ZERO, false, false, 0.21F))
                            .put(3, (entityIn) -> Drive.doSlash(entityIn, -90F, 10, Vec3.ZERO, false, 1.5f, 2f)).build())
                    .addHitEffect(StunManager::setStun)

    );
    public static final ComboState DRIVE_VERTICALL_END = register("drive_vertical_end",
            new Builder()
                    .startAndEnd(1659, 1693)
                    .priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("none"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder().put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build())
                    .releaseAction(ComboState::releaseActionQuickCharge)

    );

    public static final ComboState WAVE_EDGE_VERTICAL = register("wave_edge_vertical",
            new Builder()
                    .startAndEnd(1600, 1659)
                    .priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(ComboState.TimeoutNext.buildFromFrame(15, entity -> get("none")))
                    .nextOfTimeout(entity -> get("drive_vertical_end"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(2, (entityIn) -> AttackManager.doSlash(entityIn, -80F, Vec3.ZERO, false, false, 0.21F))
                            .put(3, (entityIn) -> WaveEdge.doSlash(entityIn, 90F, 20, Vec3.ZERO, false, 0.4F, 0.2f, 1f, 4)).build())
                    .addHitEffect(StunManager::setStun)

    );

    public static final ComboState JUDGEMENT_CUT_END = register
            (
                    "judgement_cut_end",
                    new Builder()
                            .startAndEnd(1923, 1928)
                            .priority(50)
                            .next(livingEntity -> get("judgement_cut_end"))
                            .nextOfTimeout(livingEntity -> get("judgement_cut_sheath"))
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder().put(0, mods.flammpfeil.slashblade.specialattack.JudgementCut::doJudgementCutSuper).build())
                            .addTickAction(FallHandler::fallDecrease)
                            .addHitEffect(StunManager::setStun)

            );

    public static final ComboState PIERCING = register("piercing", new Builder().startAndEnd(1, 33).priority(50).motionLoc(DefaultResources.testLocation)
            .next(entity -> get("piercing"))
            .nextOfTimeout(entity -> get("piercing_2"))
            .addTickAction(UserPoseOverrider::resetRot)
            );

    public static final ComboState PIERCING_2 = register("piercing_2", new Builder().startAndEnd(33, 55).priority(50).motionLoc(DefaultResources.testLocation)
            .next(ComboState.TimeoutNext.buildFromFrame(10, entity -> get("none")))
            .nextOfTimeout(entity -> get("piercing_end"))
            .addTickAction((entity) -> {

                long elapsed = mods.flammpfeil.slashblade.compat.SBData.get(entity.getMainHandItem(),mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new).resolvCurrentComboStateTicks(entity).getKey();

                if (elapsed < 3) {
                    entity.moveRelative(entity.isInWater() ? 0.35f : 0.8f, new Vec3(0, 0, 1));
                    AttackManager.areaAttack(entity, KnockBacks.toss.action, 1.1f, true, false, true);
                }
                if (elapsed == 1) {
                    AttackManager.playPiercingSoundAction(entity);
                }
            })
            .addTickAction(UserPoseOverrider::resetRot)
            .addHitEffect(StunManager::setStun));

    public static final ComboState PIERCING_JUST = register("piercing_just", new Builder().startAndEnd(34, 55).priority(50).motionLoc(DefaultResources.testLocation)
            .next(ComboState.TimeoutNext.buildFromFrame(10, entity -> get("none")))
            .nextOfTimeout(entity -> get("piercing_end"))
            .addTickAction((entity) -> {

                long elapsed = mods.flammpfeil.slashblade.compat.SBData.get(entity.getMainHandItem(),mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new).resolvCurrentComboStateTicks(entity).getKey();

                if (elapsed < 3) {
                    entity.moveRelative(entity.isInWater() ? 0.35f : 0.8f, new Vec3(0, 0, 1));
                    AttackManager.areaAttack(entity, KnockBacks.toss.action, 1.1f, true, false, true);
                }
                if (elapsed == 1) {
                    AttackManager.playPiercingSoundAction(entity);
                }
            })
            .addTickAction(UserPoseOverrider::resetRot)
            .addHitEffect(StunManager::setStun));

    public static final ComboState PIERCING_END = register("piercing_end",
            new Builder().startAndEnd(55, 65).priority(50)
                    .motionLoc(DefaultResources.testLocation)
                    .next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("piercing_end2"))
                    );

    public static final ComboState PIERCING_END2 = register("piercing_end2",
            new Builder().startAndEnd(65, 90).priority(50)
                    .motionLoc(DefaultResources.testLocation)
                    .next(entity -> get("none"))
                    .nextOfTimeout(entity -> get("none"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, mods.flammpfeil.slashblade.capability.slashblade.combo.Extra::playQuickSheathSoundAction).build())
                    .releaseAction(ComboState::releaseActionQuickCharge));
}
