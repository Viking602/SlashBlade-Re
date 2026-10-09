package mods.flammpfeil.slashblade.registry;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.specialattack.SlashArts;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.*;
public final class SlashArtsRegistry {
    public static final ResourceKey<Registry<SlashArts>> KEY=ResourceKey.createRegistryKey(SlashBlade.id("slash_arts"));
    public static final Registry<SlashArts> VALUES=new RegistryBuilder<>(KEY).sync(true).create();
    public static final java.util.function.Supplier<Registry<SlashArts>> REGISTRY=()->VALUES;
    public static void register(net.neoforged.bus.api.IEventBus bus){
        bus.addListener((NewRegistryEvent e)->e.register(VALUES));
        bus.addListener((RegisterEvent e)->e.register(KEY,h->{
            for(var art:java.util.List.of(SlashArts.NONE,SlashArts.JUDGEMENT_CUT,SlashArts.SAKURA_END,SlashArts.VOID_SLASH,SlashArts.CIRCLE_SLASH,SlashArts.DRIVE_VERTICAL,SlashArts.DRIVE_HORIZONTAL,SlashArts.WAVE_EDGE,SlashArts.PIERCING))h.register(SlashBlade.id(art.getName()),art);
        }));
    }
}
