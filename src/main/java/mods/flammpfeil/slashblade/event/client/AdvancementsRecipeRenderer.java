package mods.flammpfeil.slashblade.event.client;

import mods.flammpfeil.slashblade.compat.SBItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Click an advancement icon to view its server-authoritative crafting, cooking or forging recipe. */
public final class AdvancementsRecipeRenderer {
    private static final AdvancementsRecipeRenderer INSTANCE=new AdvancementsRecipeRenderer();
    public static AdvancementsRecipeRenderer getInstance() { return INSTANCE; }
    public void register() { NeoForge.EVENT_BUS.register(this); }
    private RecipeMap recipes=RecipeMap.EMPTY;
    private Identifier current;
    private record Slot(int x,int y,SlotDisplay display) {}
    private record View(Identifier texture, List<Slot> slots) {}
    @SubscribeEvent public void recipesReceived(RecipesReceivedEvent event) { recipes=event.getRecipeMap(); current=null; }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) { recipes=RecipeMap.EMPTY; current=null; }
    @SubscribeEvent public void click(ScreenEvent.MouseButtonPressed.Post event) {
        if (!(event.getScreen() instanceof AdvancementsScreen screen)) return;
        if (event.getButton()==1) { current=null; return; }
        if (event.getButton()!=0 || screen.selectedTab==null) return;
        var tab=screen.selectedTab;
        int mx=(int)(event.getMouseX()-(screen.width-252)/2-9), my=(int)(event.getMouseY()-(screen.height-140)/2-18);
        if (mx<=0 || mx>=234 || my<=0 || my>=113) return;
        current=null;
        for (AdvancementWidget widget:tab.widgets.values()) {
            if (widget.isMouseOver(Mth.floor(tab.scrollX),Mth.floor(tab.scrollY),mx,my)) {
                ItemStack icon=widget.display.getIcon().create();
                current=Identifier.tryParse(SBItemData.tag(icon).getStringOr("Crafting", ""));
                break;
            }
        }
    }
    private View view(RecipeDisplay recipe) {
        List<Slot> slots=new ArrayList<>();
        String background="crafting_table";
        if (recipe instanceof ShapedCraftingRecipeDisplay shaped) {
            slots.add(new Slot(124,35,recipe.result()));
            int ox=(3-shaped.width())/2, oy=(3-shaped.height())/2;
            for (int index=0;index<shaped.ingredients().size();index++) slots.add(new Slot(30+(index%shaped.width()+ox)*18,17+(index/shaped.width()+oy)*18,shaped.ingredients().get(index)));
        } else if (recipe instanceof ShapelessCraftingRecipeDisplay shapeless) {
            slots.add(new Slot(124,35,recipe.result()));
            for (int index=0;index<shapeless.ingredients().size();index++) slots.add(new Slot(30+index%3*18,17+index/3*18,shapeless.ingredients().get(index)));
        } else if (recipe instanceof FurnaceRecipeDisplay furnace) {
            slots.add(new Slot(116,35,recipe.result())); slots.add(new Slot(56,17,furnace.ingredient())); slots.add(new Slot(56,53,furnace.fuel()));
            var holder=recipes.byKey(ResourceKey.create(Registries.RECIPE,current));
            background=holder!=null && holder.value().getType()==RecipeType.BLASTING?"blast_furnace":holder!=null && holder.value().getType()==RecipeType.SMOKING?"smoker":"furnace";
        } else if (recipe instanceof SmithingRecipeDisplay smithing) {
            boolean anvil=current.getPath().startsWith("anvilcrafting");
            background=anvil?"anvil":"smithing";
            slots.add(new Slot(134,47,recipe.result())); slots.add(new Slot(27,47,smithing.base())); slots.add(new Slot(76,47,smithing.addition()));
            if (!anvil) slots.add(new Slot(9,47,smithing.template()));
        } else return null;
        SlotDisplay station=background.equals("anvil")?new SlotDisplay.ItemSlotDisplay(Items.ANVIL):recipe.craftingStation();
        slots.add(new Slot(5,5,station));
        return new View(Identifier.withDefaultNamespace("textures/gui/container/"+background+".png"),List.copyOf(slots));
    }
    @SubscribeEvent public void render(ScreenEvent.Render.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if (!(event.getScreen() instanceof AdvancementsScreen screen) || current==null || mc.level==null) return;
        var holder=recipes.byKey(ResourceKey.create(Registries.RECIPE,current));
        if (holder==null || holder.value().display().isEmpty()) return;
        View view=view(holder.value().display().getFirst());
        if (view==null) return;
        int x=(screen.width-176)/2, y=(screen.height-85)/2;
        var graphics=event.getGuiGraphics(); graphics.nextStratum();
        graphics.blit(RenderPipelines.GUI_TEXTURED,view.texture,x,y,0,0,176,80,256,256);
        graphics.blit(RenderPipelines.GUI_TEXTURED,view.texture,x,y+80,0,161,176,5,256,256);
        if (view.texture.getPath().equals("textures/gui/container/anvil.png")) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("container/anvil/text_field_disabled"),x+59,y+20,110,16);
        }
        var context=SlotDisplayContext.fromLevel(mc.level);
        long cycle=mc.level.getGameTime()/30;
        for (Slot slot:view.slots) {
            var options=slot.display.resolveForStacks(context);
            if (options.isEmpty()) continue;
            ItemStack item=options.get((int)(cycle%options.size()));
            int sx=x+slot.x,sy=y+slot.y;
            graphics.fakeItem(item,sx,sy); graphics.itemDecorations(mc.font,item,sx,sy);
            if (event.getMouseX()>=sx && event.getMouseX()<sx+16 && event.getMouseY()>=sy && event.getMouseY()<sy+16) graphics.setTooltipForNextFrame(mc.font,item,event.getMouseX(),event.getMouseY());
        }
    }
    private AdvancementsRecipeRenderer() {}
}
