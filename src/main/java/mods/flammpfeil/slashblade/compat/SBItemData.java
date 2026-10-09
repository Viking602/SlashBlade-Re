package mods.flammpfeil.slashblade.compat;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateCapabilityProvider;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Explicit transactions for extra item data and registry-aware stack serialization. */
public final class SBItemData {
    private static volatile HolderLookup.Provider registries;
    public static void tagsUpdated(TagsUpdatedEvent event) { registries = event.getRegistries(); }
    public static String descriptionId(ItemStack stack) { return stack.getItem() instanceof ItemSlashBlade blade ? blade.getDescriptionId(stack) : stack.getItem().getDescriptionId(); }
    public static net.minecraft.resources.RegistryOps<Tag> ops() {
        var server = ServerLifecycleHooks.getCurrentServer();
        var lookup = server == null ? registries : server.registryAccess();
        if (lookup == null) lookup = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return RegistryOps.create(NbtOps.INSTANCE, lookup);
    }
    public static CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static boolean hasTag(ItemStack stack) { return !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty(); }
    public static void update(ItemStack stack, Consumer<CompoundTag> update) { CustomData.update(DataComponents.CUSTOM_DATA, stack, update); }
    public static void put(ItemStack stack, String key, Tag value) { update(stack, tag -> tag.put(key, value.copy())); }
    /** Numeric tag widths differ between SNBT and JSON; partial predicates compare their values. */
    public static boolean matches(Tag expected, Tag actual) {
        if (expected == null) return true;
        if (actual == null) return false;
        if (expected instanceof NumericTag a && actual instanceof NumericTag b) return a.doubleValue() == b.doubleValue();
        if (expected instanceof CompoundTag a && actual instanceof CompoundTag b) {
            for (String key : a.keySet()) if (!matches(a.get(key), b.get(key))) return false;
            return true;
        }
        if (expected instanceof ListTag a && actual instanceof ListTag b) {
            if (a.isEmpty()) return b.isEmpty();
            for (Tag wanted : a) if (b.stream().noneMatch(value -> matches(wanted, value))) return false;
            return true;
        }
        return expected.equals(actual);
    }
    public static CompoundTag save(ItemStack stack) {
        if (stack.isEmpty()) return new CompoundTag();
        return (CompoundTag)ItemStack.CODEC.encodeStart(ops(), stack).getOrThrow();
    }
    public static com.google.gson.JsonObject toJson(ItemStack stack) {
        return NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, save(stack)).getAsJsonObject();
    }
    public static ItemStack load(CompoundTag tag) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        if (tag.contains("components") || tag.contains("count")) return ItemStack.CODEC.parse(ops(), tag).getOrThrow();
        // Upstream anvil materials contain pre-component stack descriptions.
        Identifier id = Identifier.tryParse(tag.getStringOr("id", "minecraft:air"));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(item, tag.getByteOr("Count", (byte)1));
        CompoundTag custom = tag.getCompoundOrEmpty("tag");
        if (!custom.isEmpty()) CustomData.set(DataComponents.CUSTOM_DATA, result, custom);
        CompoundTag caps = tag.getCompoundOrEmpty("ForgeCaps").getCompoundOrEmpty("slashblade:bladestate");
        if (!caps.isEmpty()) SBData.get(result, ItemSlashBlade.BLADESTATE).ifPresent(state -> new BladeStateCapabilityProvider(state).deserializeNBT(caps));
        return result;
    }
    private SBItemData() {}
}
