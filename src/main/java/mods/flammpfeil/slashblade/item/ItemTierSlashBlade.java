package mods.flammpfeil.slashblade.item;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
/** Resharped base durability, no mining speed, enchantability 10. */
public final class ItemTierSlashBlade {
    private final int durability;
    public ItemTierSlashBlade() { this(40); }
    public ItemTierSlashBlade(int durability) { this.durability=durability; }
    public ToolMaterial material() {
        return new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, durability, 0, 0, 10,
            ItemTags.create(Identifier.fromNamespaceAndPath("slashblade", "proudsouls")));
    }
}
