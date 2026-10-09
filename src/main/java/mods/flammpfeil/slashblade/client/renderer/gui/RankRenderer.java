package mods.flammpfeil.slashblade.client.renderer.gui;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.concentrationrank.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

public final class RankRenderer {
    private static final RankRenderer INSTANCE = new RankRenderer();
    public static RankRenderer getInstance() { return INSTANCE; }
    public void register() { NeoForge.EVENT_BUS.register(this); }
    private static final Identifier TEXTURE = SlashBlade.id("textures/gui/rank.png");
    @SubscribeEvent public void renderTick(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        SBData.get(mc.player, CapabilityConcentrationRank.RANK_POINT).ifPresent(rankState -> {
            long now=mc.player.level().getGameTime();
            var rank=rankState.getRank(now);
            if (rank == IConcentrationRank.ConcentrationRanks.NONE || now >= rankState.getLastUpdate()+120) return;
            int x=mc.getWindow().getGuiScaledWidth()*2/3, y=mc.getWindow().getGuiScaledHeight()/5;
            int row=32*(rank.level-1), text=now<rankState.getLastRankRise()+20?128:0;
            int progress=(int)(33*rankState.getRankProgress(now));
            int icon=(int)(18*rankState.getRankProgress(now)), inverse=17-icon;
            var graphics=event.getGuiGraphics();
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x,y,text+64,row,64,32,256,256);
            if (icon>0) graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,x,y+inverse+7,text,row+inverse+7,64,icon,256,256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,x,y+32,0,240,64,16,256,256);
            if (progress>0) graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,x+16,y+32,16,224,progress,16,256,256);
        });
    }
    private RankRenderer() {}
}
