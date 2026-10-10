package mods.flammpfeil.slashblade.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import mods.flammpfeil.slashblade.SlashBlade;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.function.Consumer;

/** Data-driven special item renderer; display context is selected by the client item definition. */
public final class BladeSpecialRenderer implements SpecialModelRenderer<BladeSpecialRenderer.Argument> {
    private final ItemDisplayContext context;
    private final SlashBladeTEISR renderer;
    BladeSpecialRenderer(String context) {
        this.context = ItemDisplayContext.valueOf(context.toUpperCase(java.util.Locale.ROOT));
        renderer = new SlashBladeTEISR();
    }
    @Override public Argument extractArgument(ItemStack stack) { return new Argument(stack); }
    @Override public void getExtents(Consumer<Vector3fc> output) {
        // TEISR places the scaled OBJ around (0.5, 0.5, 0.5), before the
        // item's display transform. Supply all corners so rotations retain
        // the full bounds of the blade, scabbard and durability ring.
        float scale = context == ItemDisplayContext.GUI ? .008f : context == ItemDisplayContext.GROUND ? .005f : .0095f;
        float centerY = context == ItemDisplayContext.GROUND ? .65f : .5f;
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            output.accept(new Vector3f(.5f + x * 88 * scale, centerY + y * 88 * scale, .5f + z * 32 * scale));
        }
    }
    @Override public void submit(Argument argument, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        if (argument == null) return;
        // First-person geometry is submitted by RenderHandEvent before vanilla
        // held-item transforms. Special item rendering retains the usual contexts.
        if (context.firstPerson()) return;
        GeometryBuffer geometry = new GeometryBuffer();
        renderer.renderByItem(argument.snapshot, context, new PoseStack(), geometry, light, overlay);
        geometry.submit(pose, collector);
    }

    /** Immutable cache identity with a separate deferred-rendering snapshot. */
    public static final class Argument {
        private final ItemStack snapshot;
        private final Item item;
        private final int count;
        private final DataComponentMap components;
        private final int hash;
        Argument(ItemStack stack) {
            snapshot = stack.copy();
            item = stack.getItem();
            count = stack.getCount();
            components = stack.immutableComponents();
            hash = 31 * (31 * item.hashCode() + count) + components.hashCode();
        }
        @Override public boolean equals(Object other) {
            return other instanceof Argument argument && item == argument.item && count == argument.count
                && components.equals(argument.components);
        }
        @Override public int hashCode() { return hash; }
    }

    public record Unbaked(String context) implements SpecialModelRenderer.Unbaked<Argument> {
        public static final MapCodec<Unbaked> CODEC = Codec.STRING.optionalFieldOf("context", "gui").xmap(Unbaked::new, Unbaked::context);
        @Override public SpecialModelRenderer<Argument> bake(BakingContext context) {
            return new BladeSpecialRenderer(this.context);
        }
        @Override public MapCodec<Unbaked> type() { return CODEC; }
    }
}
