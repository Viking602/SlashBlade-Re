package mods.flammpfeil.slashblade.compat;

import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.function.Supplier;

public final class SBRecipes {
    private static final DeferredRegister<IngredientType<?>> INGREDIENTS = DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, "slashblade");
    public static final Supplier<IngredientType<BladeIngredient>> BLADE_INGREDIENT = INGREDIENTS.register("blade_ingredient", () -> new IngredientType<>(BladeIngredient.CODEC));
    private static final DeferredRegister<DataComponentPredicate.Type<?>> PREDICATES = DeferredRegister.create(Registries.DATA_COMPONENT_PREDICATE_TYPE, "slashblade");
    public static final Supplier<DataComponentPredicate.Type<BladePredicate>> BLADE_PREDICATE = PREDICATES.register("blade", () -> new DataComponentPredicate.ConcreteType<>(BladePredicate.CODEC));
    public static void register(IEventBus bus) {
        INGREDIENTS.register(bus); PREDICATES.register(bus);
        // NeoForge transmits the authoritative recipes again on each data-pack reload.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent event) ->
            event.sendRecipes(RecipeType.CRAFTING, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING, RecipeType.SMITHING));
    }
    private SBRecipes() {}
}
