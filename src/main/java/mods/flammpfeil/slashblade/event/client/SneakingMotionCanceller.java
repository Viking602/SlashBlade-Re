package mods.flammpfeil.slashblade.event.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Applied during extraction so model pose and render offset use the same crouching state. */
public final class SneakingMotionCanceller {
    public static void apply(AvatarRenderState state) { state.isCrouching = false; }
    private SneakingMotionCanceller() {}
}
