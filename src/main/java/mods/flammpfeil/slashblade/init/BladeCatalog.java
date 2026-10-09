package mods.flammpfeil.slashblade.init;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateCapabilityProvider;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;

/** Named creative variants derived from the upstream recipe results. */
public final class BladeCatalog {
    public static void displayItems(CreativeModeTab.Output output) {
        items().forEach(stack -> output.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
    public static java.util.List<ItemStack> items() {
        var result=new java.util.ArrayList<ItemStack>();
        try (var input = BladeCatalog.class.getResourceAsStream("/data/slashblade/blade_catalog.json")) {
            if (input == null) throw new IllegalStateException("Missing blade catalog");
            var entries = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var entry : entries) {
                CompoundTag tag = CompoundTag.CODEC.parse(JsonOps.INSTANCE, entry).getOrThrow();
                ItemStack stack = new ItemStack(SBItems.slashblade);
                SBData.get(stack, ItemSlashBlade.BLADESTATE).ifPresent(state -> new BladeStateCapabilityProvider(state).deserializeNBT(tag));
                result.add(stack);
            }
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        return result;
    }
    private BladeCatalog() {}
}
