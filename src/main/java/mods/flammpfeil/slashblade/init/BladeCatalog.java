package mods.flammpfeil.slashblade.init;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;

/** Built-ins converted from Resharped 6e2a0a0, including sealed/broken variants. */
public final class BladeCatalog {
    private static final JsonArray DEFINITIONS=read();
    private static JsonArray read() {
        try(var input=BladeCatalog.class.getResourceAsStream("/data/slashblade/blade_catalog.json")) {
            if(input==null) throw new IllegalStateException("Missing blade catalog");
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonArray();
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public static void initializeBase(Item item,ISlashBladeState state) {
        String id=BuiltInRegistries.ITEM.getKey(item).getPath();
        for(var entry:DEFINITIONS) {
            var definition=entry.getAsJsonObject();
            if(!definition.get("key").getAsString().equals(id)) continue;
            var tag=CompoundTag.CODEC.parse(JsonOps.INSTANCE,definition.getAsJsonObject("stack").getAsJsonObject("components").get("slashblade:blade_state")).getOrThrow();
            new BladeStateCapabilityProvider(state).deserializeNBT(tag);return;
        }
    }
    public static ItemStack blade(String id) {
        for(var entry:DEFINITIONS) {
            var definition=entry.getAsJsonObject();
            if(definition.get("key").getAsString().equals(id))
                return SBItemData.load(CompoundTag.CODEC.parse(JsonOps.INSTANCE,definition.get("stack")).getOrThrow());
        }
        throw new IllegalArgumentException("Unknown built-in blade: "+id);
    }
    public static List<ItemStack> items() {
        var result=new ArrayList<ItemStack>();
        for(var entry:DEFINITIONS) result.add(blade(entry.getAsJsonObject().get("key").getAsString()));
        return result;
    }
    public static void displayItems(CreativeModeTab.Output output) {
        items().forEach(stack -> output.accept(stack,CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
    /** Only explicit pre-port.25 identities are aliases; custom blades stay distinct. */
    public static String canonicalName(String name) {
        return switch(name) {
            case "item.slashblade.slashblade", "item.slashblade.simple.iron", "item.slashblade.simple.wood", "item.slashblade.simple.bamboo", "item.slashblade.simple.silverbamboo", "item.slashblade.simple.white" -> "";
            case "item.slashblade.ex.fox.black" -> "item.slashblade.fox_black";
            case "item.slashblade.ex.fox.white" -> "item.slashblade.fox_white";
            case "item.slashblade.ex.tukumo" -> "item.slashblade.yuzukitukumo";
            case "item.slashblade.ex.ruby", "item.slashblade.ex.muramasa", "item.slashblade.ex.sabigatana", "item.slashblade.ex.doutanuki" -> name.replace(".ex.",".");
            default -> name;
        };
    }
    public static String baseItemIdentity(ItemStack stack) {
        var state=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        if(stack.is(SBItems.slashblade) && state.getTranslationKey().startsWith("item.slashblade.simple.")) {
            String suffix=state.getTranslationKey().substring("item.slashblade.simple.".length());
            if(Set.of("wood","bamboo","silverbamboo","white").contains(suffix)) return "slashblade:slashblade_"+suffix;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    private BladeCatalog() {}
}
