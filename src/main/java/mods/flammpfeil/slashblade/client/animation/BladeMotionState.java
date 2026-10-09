package mods.flammpfeil.slashblade.client.animation;

import java.util.UUID;
import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import net.minecraft.world.entity.LivingEntity;

/** Shared render clock for sword and body. Weak keys do not retain departed entities. */
public final class BladeMotionState {
    public static final double TRANSITION_TICKS = 1.5;
    public static final double DRAW_TICKS = 4.5, SHEATH_TICKS = 18.0;
    private static final WeakHashMap<LivingEntity, History> histories = new WeakHashMap<>();

    public record Sample(BladeAnimationTimeline current, Sample previous, float alpha) {
        public static Sample direct(BladeAnimationTimeline timeline) { return new Sample(timeline, null, 1); }
        public boolean blending() { return previous != null && alpha < 1; }
        private Sample advance(double ticks) {
            var combo = current.combo();
            float frame = Math.min(combo.getEndFrame(), current.frame() + (float)(ticks * 1.5 * combo.getSpeed()));
            return new Sample(new BladeAnimationTimeline(combo, frame),
                    previous == null ? null : previous.advance(ticks), alpha);
        }
        private Sample bounded(int depth) {
            if (!blending()) return this;
            if (alpha == 0) return previous.bounded(depth);
            return depth == 0 ? direct(current) : new Sample(current, previous.bounded(depth - 1), alpha);
        }
    }

    public static final class History {
        private UUID blade;
        private long action;
        private double lastTime = Double.NaN, transitionStart, transitionDuration = TRANSITION_TICKS;
        private BladeAnimationTimeline last;
        private Sample previous;
        private Sample cached;
        private boolean stagedTransition;
        private boolean freezePrevious;

        public Sample resolve(UUID blade, long action, double time, BladeAnimationTimeline target) {
            if (last == null || !blade.equals(this.blade) || time < lastTime) {
                this.blade = blade; this.action = action; lastTime = time;
                last = target; previous = null; cached = Sample.direct(target);
                return cached;
            }
            if (time == lastTime && action == this.action && target.equals(last)) return cached;
            if (action != this.action || target.combo() != last.combo()) {
                // Adjacent recovery segments already share a continuous VMD frame.
                boolean adjacent = action == this.action && target.combo().getMotionLoc().equals(last.combo().getMotionLoc())
                        && Math.abs(target.frame() - last.frame()) <= 3.0F && KatanaChoreography.continuous(last,target);
                // Freeze the displayed blend, rather than the unblended target,
                // when an action interrupts the preceding transition. Bound the
                // retained history; zero-weight actions never add a node.
                // Crossing an authored recovery boundary must not discard a still
                // active entry blend. That used to snap to the target mid-transition.
                if (!adjacent) {
                    var displayed=PlayerBladeAnimation.sample(cached);
                    boolean embedded=displayed!=null && KatanaChoreography.containsBlade(displayed.score());
                    previous = cached.bounded(16);
                    transitionStart = time;
                    transitionDuration = standby(target) ? SHEATH_TICKS : standby(last) ? DRAW_TICKS
                            : embedded ? DRAW_TICKS*(1-KatanaChoreography.DRAW_CLEAR*KatanaChoreography.withdrawal(displayed.score())) : TRANSITION_TICKS;
                    if(KatanaChoreography.judgementRelease(target.combo().getName()) && (standby(last)||embedded))
                        transitionDuration=2.0;
                    stagedTransition = standby(target) || standby(last) || embedded;
                    freezePrevious=embedded;
                }
            }
            float progress = (float)Math.clamp((time - transitionStart) / transitionDuration, 0, 1);
            // Draw/noto already contain staged easing. Easing this clock again crushes
            // the actual withdrawal into one or two frames and stalls both ends.
            float alpha = stagedTransition ? progress : progress * progress * progress * (progress * (progress * 6 - 15) + 10);
            if (alpha >= 1) previous = null;
            this.action = action; lastTime = time; last = target;
            // Keep the outgoing movement alive during its fade. Freezing the source
            // every time a combo changes makes the arms visibly brake before the next cut.
            cached = new Sample(target, previous == null ? null : freezePrevious ? previous : previous.advance(time - transitionStart), previous == null ? 1 : alpha);
            return cached;
        }
    }

    private static boolean standby(BladeAnimationTimeline t) {
        return t.combo() == mods.flammpfeil.slashblade.capability.slashblade.combo.Extra.STANDBY_EX
                || t.combo() == mods.flammpfeil.slashblade.capability.slashblade.combo.Extra.STANDBY_INAIR;
    }

    public static Sample sample(LivingEntity entity, ISlashBladeState blade, float partialTick) {
        if (mods.flammpfeil.slashblade.verification.BladeVisualClientProbe.ENABLED) {
            Sample scripted = mods.flammpfeil.slashblade.verification.BladeVisualClientProbe.sampleOverride(entity);
            if (scripted != null) return scripted;
        }
        var timeline = BladeAnimationTimeline.resolve(blade, entity.level().getGameTime(), partialTick);
        return histories.computeIfAbsent(entity, ignored -> new History()).resolve(blade.getUniqueId(),
                blade.getLastActionTime(), entity.level().getGameTime() + (double)partialTick, timeline);
    }
    public static void clear() { histories.clear(); }
    private BladeMotionState() {}
}
