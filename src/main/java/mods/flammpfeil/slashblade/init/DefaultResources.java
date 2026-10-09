package mods.flammpfeil.slashblade.init;

import mods.flammpfeil.slashblade.SlashBlade;
import net.minecraft.resources.Identifier;

public interface DefaultResources{
    Identifier BaseMotionLocation = Identifier.fromNamespaceAndPath(SlashBlade.modid, "combostate/old_motion.vmd");
    Identifier ExMotionLocation = Identifier.fromNamespaceAndPath(SlashBlade.modid, "combostate/motion.vmd");
}
