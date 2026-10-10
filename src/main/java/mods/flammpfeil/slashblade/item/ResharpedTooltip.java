package mods.flammpfeil.slashblade.item;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Resharped's effect name and required-level display, using NeoForge's player-aware tooltip context. */
public final class ResharpedTooltip {
    public static void effect(Identifier id, Item.TooltipContext context, Consumer<Component> tooltip, boolean showZero) {
        var effect=SpecialEffectsRegistry.VALUES.getValue(id);
        var player=context.player();
        if(effect==null || player==null)return;
        int level=effect.getRequestLevel();
        var levelText=Component.literal(showZero || level>0?String.valueOf(level):"")
                .withStyle(SpecialEffect.isEffective(effect,player.experienceLevel)?ChatFormatting.RED:ChatFormatting.DARK_GRAY);
        tooltip.accept(Component.translatable("slashblade.tooltip.special_effect",effect.getDescription(),levelText).withStyle(ChatFormatting.GRAY));
    }
    private ResharpedTooltip() {}
}
