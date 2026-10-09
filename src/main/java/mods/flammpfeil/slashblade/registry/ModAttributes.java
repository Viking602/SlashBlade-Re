package mods.flammpfeil.slashblade.registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.neoforge.registries.*;
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES=DeferredRegister.create(Registries.ATTRIBUTE,"slashblade");
    public static final DeferredHolder<Attribute,Attribute> SLASHBLADE_DAMAGE=ATTRIBUTES.register("slashblade_damage",()->new RangedAttribute("attribute.name.generic.slashblade_damage",1,0,512).setSyncable(true));
}
