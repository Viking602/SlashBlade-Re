package mods.flammpfeil.slashblade.util;

import com.google.gson.*;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Developer toolbar export using current item components and advancement predicates. */
public class AdvancementBuilder {
    public static String getAdvancementJsonStr(ItemStack input) {
        ItemStack icon = input.copy(); JsonObject result = new JsonObject();
        SBData.get(icon, ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            SBItemData.update(icon, tag -> tag.putString("Crafting", "slashblade:replace_recipe_id"));
            JsonObject display = new JsonObject(); display.add("icon", SBItemData.toJson(icon));
            JsonObject title = new JsonObject(); title.addProperty("translate", state.getTranslationKey()); display.add("title", title);
            JsonObject description = new JsonObject(); description.addProperty("translate", state.getTranslationKey()+".desc"); display.add("description", description);
            display.addProperty("frame", "task"); result.add("display", display); result.addProperty("parent", "slashblade:root");
            JsonObject blade = new JsonObject(); blade.addProperty("translationKey", state.getTranslationKey()); blade.addProperty("isBroken", 0);
            JsonObject predicates = new JsonObject(); predicates.add("slashblade:blade", blade);
            JsonObject item = new JsonObject(); item.addProperty("items", BuiltInRegistries.ITEM.getKey(input.getItem()).toString()); item.add("predicates", predicates);
            JsonArray items = new JsonArray(); items.add(item); JsonObject conditions = new JsonObject(); conditions.add("items", items);
            JsonObject crafting = new JsonObject(); crafting.addProperty("trigger", "minecraft:inventory_changed"); crafting.add("conditions", conditions);
            JsonObject criteria = new JsonObject(); criteria.add("crafting", crafting); result.add("criteria", criteria);
        });
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result);
    }
}
