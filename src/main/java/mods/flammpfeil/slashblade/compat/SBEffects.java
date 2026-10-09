package mods.flammpfeil.slashblade.compat;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/** Version-specific sound notifications and persisted projectile potion effects. */
public final class SBEffects {
    public static void notifySound(Player player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source, player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
        } else player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), sound, source, volume, pitch, false);
    }
    public static List<MobEffectInstance> effects(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new ArrayList<MobEffectInstance>();
        var id = Identifier.tryParse(tag.getStringOr("Potion", "minecraft:empty"));
        if (id != null) registries.lookupOrThrow(Registries.POTION).get(ResourceKey.create(Registries.POTION, id))
            .ifPresent(potion -> potion.value().getEffects().forEach(effect -> result.add(new MobEffectInstance(effect))));
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (Tag value : tag.getListOrEmpty("CustomPotionEffects"))
            MobEffectInstance.CODEC.parse(ops, value).result().ifPresent(result::add);
        return result;
    }
    public static CompoundTag saveEffect(MobEffectInstance effect, HolderLookup.Provider registries) {
        return (CompoundTag)MobEffectInstance.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), effect).getOrThrow();
    }
    private SBEffects() {}
}
