package mods.flammpfeil.slashblade.client.renderer.model;

import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.util.TimeValueHelper;

/** The sword and player sample the same synchronized action clock, including recovery states. */
public record BladeAnimationTimeline(ComboState combo, float frame) {
    public static BladeAnimationTimeline resolve(ISlashBladeState state, long gameTime, float partialTick) {
        double time = TimeValueHelper.getMSecFromTicks(Math.max(0L, gameTime - state.getLastActionTime()) + partialTick);
        ComboState combo = state.getComboSeq();
        while (combo != ComboState.NONE && combo.getTimeoutMS() < time) {
            time -= combo.getTimeoutMS();
            combo = combo.getNextOfTimeout();
        }
        if (combo == ComboState.NONE) combo = state.getComboRoot();
        double span = Math.abs(combo.getEndFrame() - combo.getStartFrame());
        double frames = time * 0.03 * combo.getSpeed();
        frames = combo.getLoop() && span > 0 ? frames % span : Math.min(span, frames);
        return new BladeAnimationTimeline(combo, (float)(combo.getStartFrame() + frames));
    }
}
