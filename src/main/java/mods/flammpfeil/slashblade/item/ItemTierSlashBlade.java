package mods.flammpfeil.slashblade.item;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
/** The upstream blade material: 100 durability, no mining speed, enchantability 10. */
public final class ItemTierSlashBlade {
    public ToolMaterial material() {
        return new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 100, 0, 0, 10,
            ItemTags.create(Identifier.fromNamespaceAndPath("slashblade", "proudsouls")));
    }
}
