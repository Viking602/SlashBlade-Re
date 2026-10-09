package mods.flammpfeil.slashblade.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class SlashBladeKeys {
    public static final KeyMapping SUPER_SA=new KeyMapping("key.slashblade.super_sa",GLFW.GLFW_KEY_V,KeyMapping.Category.GAMEPLAY);
    public static void register(RegisterKeyMappingsEvent event) {
        SUPER_SA.setKeyConflictContext(KeyConflictContext.IN_GAME);
        event.register(SUPER_SA);
    }
    private SlashBladeKeys() {}
}
