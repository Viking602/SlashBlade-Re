package mods.flammpfeil.slashblade.specialattack;

import com.google.common.collect.Maps;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.RangeAttack;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.util.RegistryBase;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class SlashArts extends RegistryBase<SlashArts> {
    static Map<Identifier, SlashArts> registry = Maps.newHashMap();

    @Override
    public Map<Identifier, SlashArts> getRegistry() {
        return SlashArts.registry;
    }

    static public final int ChargeTicks = 9;
    static public final int ChargeJustTicks = 3;
    static public final int ChargeJustTicksMax = 5;

    static public int getJustReceptionSpan(LivingEntity user){
        return Math.min(ChargeJustTicksMax , ChargeJustTicks + mods.flammpfeil.slashblade.compat.SBEnchantments.livingLevel(Enchantments.SOUL_SPEED,user));
    }

    public enum ArtsType{
        Fail,
        Success,
        Jackpot,
        Broken, Super
    }

    public static final SlashArts NONE = new SlashArts(BaseInstanceName, (e)->ComboState.NONE);

    public static final SlashArts JUDGEMENT_CUT =
            new SlashArts("judgement_cut", (e)-> e.onGround() ? Extra.EX_JUDGEMENT_CUT : Extra.EX_JUDGEMENT_CUT_SLASH_AIR)
                    .setComboStateJust((e)->Extra.EX_JUDGEMENT_CUT_SLASH_JUST)
                    .setComboStateBroken((e)->Extra.EX_VOID_SLASH);

    public static final SlashArts SAKURA_END=new SlashArts("sakura_end",e->e.onGround()?mods.flammpfeil.slashblade.slasharts.ResharpedCombos.SAKURA_END_LEFT:mods.flammpfeil.slashblade.slasharts.ResharpedCombos.SAKURA_END_LEFT_AIR);
    public static final SlashArts VOID_SLASH=new SlashArts("void_slash",e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.VOID_SLASH);
    public static final SlashArts CIRCLE_SLASH=new SlashArts("circle_slash",e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.CIRCLE_SLASH);
    public static final SlashArts DRIVE_VERTICAL=new SlashArts("drive_vertical",e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.DRIVE_VERTICAL);
    public static final SlashArts DRIVE_HORIZONTAL=new SlashArts("drive_horizontal",e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.DRIVE_HORIZONTAL);
    public static final SlashArts WAVE_EDGE=new SlashArts("wave_edge",e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.WAVE_EDGE_VERTICAL);
    public static final SlashArts PIERCING=new SlashArts("piercing",e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.PIERCING).setComboStateJust(e->mods.flammpfeil.slashblade.slasharts.ResharpedCombos.PIERCING_JUST);
    private int proudSoulCost=20;
    public int getProudSoulCost(){return proudSoulCost;}
    public SlashArts setProudSoulCost(int value){proudSoulCost=Math.max(0,value);return this;}
    @Override public SlashArts valueOf(String name){
        if(name==null || name.isBlank()) return JUDGEMENT_CUT;
        var id=Identifier.tryParse(name.indexOf(':')>=0?name:"slashblade:"+name);
        if(id==null) return null;
        if(!id.getNamespace().equals("slashblade")) return mods.flammpfeil.slashblade.registry.SlashArtsRegistry.VALUES.getValue(id);
        return super.valueOf(id.getPath().replace("slasharts/",""));
    }
    public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.translatable("slash_art.slashblade."+getName());}
    private Function<LivingEntity,ComboState> comboState;
    private Function<LivingEntity,ComboState> comboStateJust;
    private Function<LivingEntity,ComboState> comboStateBroken;

    public ComboState doArts(ArtsType type, LivingEntity user) {
        switch (type){
            case Super: return mods.flammpfeil.slashblade.slasharts.ResharpedCombos.JUDGEMENT_CUT_END;
            case Jackpot:
                return getComboStateJust(user);
            case Success:
                return getComboState(user);
            case Broken:
                return getComboStateBroken(user);
        }
        return ComboState.NONE;
    }

    public SlashArts(String name, Function<LivingEntity,ComboState> state) {
        super(name);

        this.comboState = state;
        this.comboStateJust = state;
        this.comboStateBroken = state;
    }

    @Override
    public String getPath() {
        return "slasharts";
    }

    @Override
    public SlashArts getNone() {
        return NONE;
    }

    public ComboState getComboState(LivingEntity user) {
        return this.comboState.apply(user);
    }

    public ComboState getComboStateJust(LivingEntity user) {
        return this.comboStateJust.apply(user);
    }
    public SlashArts setComboStateJust(Function<LivingEntity,ComboState> state){
        this.comboStateJust = state;
        return this;
    }

    public ComboState getComboStateBroken(LivingEntity user) {
        return this.comboStateBroken.apply(user);
    }
    public SlashArts setComboStateBroken(Function<LivingEntity,ComboState> state){
        this.comboStateBroken = state;
        return this;
    }
}
