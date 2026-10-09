package mods.flammpfeil.slashblade.registry;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.registry.specialeffects.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.*;
public final class SpecialEffectsRegistry {
    public static final ResourceKey<Registry<SpecialEffect>> KEY=ResourceKey.createRegistryKey(SlashBlade.id("special_effect"));
    public static final Registry<SpecialEffect> VALUES=new RegistryBuilder<>(KEY).sync(true).create();
    public static final java.util.function.Supplier<Registry<SpecialEffect>> REGISTRY=()->VALUES;
    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECT=DeferredRegister.create(KEY,"slashblade");
    public static final DeferredHolder<SpecialEffect,WitherEdge> WITHER_EDGE=SPECIAL_EFFECT.register("wither_edge",WitherEdge::new);
    public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener((NewRegistryEvent e)->e.register(VALUES));SPECIAL_EFFECT.register(bus);}
}
