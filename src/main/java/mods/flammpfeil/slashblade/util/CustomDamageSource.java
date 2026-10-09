package mods.flammpfeil.slashblade.util;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;

import org.jetbrains.annotations.Nullable;

public class CustomDamageSource {
    public static final ResourceKey<DamageType> SUMMONED_SWORD = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(SlashBlade.modid, "summonedsword"));

}
