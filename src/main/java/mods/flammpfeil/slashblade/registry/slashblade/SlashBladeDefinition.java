package mods.flammpfeil.slashblade.registry.slashblade;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
/** Resharped's named_blades data schema, synchronized by NeoForge's registry protocol. */
public record SlashBladeDefinition(Identifier item,Identifier name,Render render,Properties properties,List<Enchant> enchantments,Identifier creativeGroup) {
    public static final ResourceKey<Registry<SlashBladeDefinition>> REGISTRY_KEY=ResourceKey.createRegistryKey(SlashBlade.id("named_blades"));
    public static final Codec<SlashBladeDefinition> CODEC=RecordCodecBuilder.create(i->i.group(
        Identifier.CODEC.optionalFieldOf("item",SlashBlade.id("slashblade")).forGetter(SlashBladeDefinition::item),
        Identifier.CODEC.fieldOf("name").forGetter(SlashBladeDefinition::name),Render.CODEC.fieldOf("render").forGetter(SlashBladeDefinition::render),
        Properties.CODEC.fieldOf("properties").forGetter(SlashBladeDefinition::properties),Enchant.CODEC.listOf().optionalFieldOf("enchantments",List.of()).forGetter(SlashBladeDefinition::enchantments),
        Identifier.CODEC.optionalFieldOf("creativeGroup",SlashBlade.id("slashblade")).forGetter(SlashBladeDefinition::creativeGroup)
    ).apply(i,SlashBladeDefinition::new));
    public record Render(Identifier texture,Identifier model,int color,boolean inverse,String carry){
        public static final Codec<Render> CODEC=RecordCodecBuilder.create(i->i.group(
            Identifier.CODEC.optionalFieldOf("texture",SlashBlade.id("model/blade.png")).forGetter(Render::texture),
            Identifier.CODEC.optionalFieldOf("model",SlashBlade.id("model/blade.obj")).forGetter(Render::model),
            Codec.INT.optionalFieldOf("summon_sword_color",0xFF3333FF).forGetter(Render::color),Codec.BOOL.optionalFieldOf("color_inverse",false).forGetter(Render::inverse),
            Codec.STRING.optionalFieldOf("carry_type","default").forGetter(Render::carry)).apply(i,Render::new));
    }
    public record Properties(Identifier root,Identifier art,float attack,int durability,List<String> types,List<Identifier> effects,boolean unbreakable){
        public static final Codec<Properties> CODEC=RecordCodecBuilder.create(i->i.group(
            Identifier.CODEC.optionalFieldOf("root_combo",SlashBlade.id("standby")).forGetter(Properties::root),
            Identifier.CODEC.optionalFieldOf("slash_art",SlashBlade.id("judgement_cut")).forGetter(Properties::art),Codec.floatRange(0,1000000).optionalFieldOf("attack_base",4f).forGetter(Properties::attack),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("max_damage",40).forGetter(Properties::durability),Codec.STRING.listOf().optionalFieldOf("sword_type",List.of()).forGetter(Properties::types),
            Identifier.CODEC.listOf().optionalFieldOf("special_effects",List.of()).forGetter(Properties::effects),Codec.BOOL.optionalFieldOf("unbreakable",false).forGetter(Properties::unbreakable)
        ).apply(i,Properties::new));
    }
    public record Enchant(Identifier id,int level){
        public static final Codec<Enchant> CODEC=RecordCodecBuilder.create(i->i.group(Identifier.CODEC.fieldOf("id").forGetter(Enchant::id),Codec.intRange(1,255).optionalFieldOf("lvl",1).forGetter(Enchant::level)).apply(i,Enchant::new));
    }
    public ItemStack getBlade(){return getBlade(SBItemData.registries());}
    public ItemStack getBlade(HolderLookup.Provider access){
        var value=BuiltInRegistries.ITEM.getValue(item);if(!(value instanceof ItemSlashBlade))return ItemStack.EMPTY;
        var pre=new mods.flammpfeil.slashblade.event.SlashBladeRegistryEvent.Pre(this);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(pre).isCanceled())return ItemStack.EMPTY;
        var result=new ItemStack(value);var state=SBData.get(result,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setBaseAttackModifier(properties.attack());state.setMaxDamage(properties.durability());state.setSlashArtsKey(properties.art().toString());state.setSpecialEffects(new LinkedHashSet<>(properties.effects()));
        state.setDefaultBewitched(properties.types().contains("bewitched"));state.setSealed(properties.types().contains("sealed"));
        if(properties.types().contains("broken")){state.setDamage(1);state.setBroken(true);}
        state.setModel(render.model());state.setTexture(render.texture());state.setColorCode(render.color());state.setEffectColorInverse(render.inverse());
        try{state.setCarryType(CarryType.valueOf(render.carry().toUpperCase(Locale.ROOT)));}catch(IllegalArgumentException ignored){state.setCarryType(CarryType.DEFAULT);}
        state.setTranslationKey(name.equals(SlashBlade.id("none"))?"":"item."+name.toString().replace(':','.'));
        if(!properties.root().equals(SlashBlade.id("standby")))state.setComboRootName(properties.root().getPath());
        for(var e:enchantments)access.lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT,e.id())).ifPresent(h->result.enchant(h,e.level()));
        if(properties.unbreakable())result.set(DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
        SBItemData.put(result,"resharped_definition",StringTag.valueOf(name.getNamespace().equals("slashblade")?name.getPath():name.toString()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.SlashBladeRegistryEvent.Post(this,result));return result;
    }
    public Identifier getItemName(){return item;} public Identifier getName(){return name;}
}
