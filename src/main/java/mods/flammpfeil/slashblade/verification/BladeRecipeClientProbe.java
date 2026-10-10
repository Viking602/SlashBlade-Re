package mods.flammpfeil.slashblade.verification;

import java.nio.file.*;
import java.util.*;
import mezz.jei.api.constants.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.compat.jei.SlashBladeJeiPlugin;
import mods.flammpfeil.slashblade.init.BladeCatalog;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opt-in verification of actual JEI lookups/layouts and GPU-rendered item icons. */
public final class BladeRecipeClientProbe {
    private static final String[] shots={"catalog-icons","recipe-white","recipe-ruby","recipe-fox-black","recipe-doutanuki","recipe-anvil","tooltip-tukumo"};
    private static boolean active,pending;
    private static int stage,frames;
    public static void register() {
        if (!Boolean.getBoolean("slashblade.recipeShowcase")) return;
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> {
            if (!active || pending || ++frames<20) return;
            var mc=Minecraft.getInstance();pending=true;
            var path=mc.gameDirectory.toPath().resolve("screenshots/"+shots[stage]+".png");
            Screenshot.takeScreenshot(mc.getMainRenderTarget(),image -> {
                try(image) { Files.createDirectories(path.getParent());image.writeToFile(path); }
                catch(Exception e) { throw new IllegalStateException(e); }
                mc.execute(() -> { pending=false;frames=0; if(++stage==shots.length) { active=false;mc.stop(); } else show(); });
            });
        });
    }
    public static void start() {
        var mc=Minecraft.getInstance();
        var report=verify();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("recipe-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch(Exception e) { throw new IllegalStateException(e); }
        active=true;stage=0;frames=0;show();
    }
    public static Map<String,Object> verify() {
        var runtime=Objects.requireNonNull(SlashBladeJeiPlugin.runtime(),"JEI runtime unavailable");
        var manager=runtime.getRecipeManager();var category=manager.getRecipeCategory(RecipeTypes.CRAFTING);
        var catalog=BladeCatalog.items();var helper=runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
        require(catalog.stream().map(s -> helper.getUid(s,UidContext.Recipe)).distinct().count()==31,"JEI merged named blades");
        int count=0;
        for(var holder:manager.createRecipeLookup(RecipeTypes.CRAFTING).get().toList()) {
            if (!holder.id().identifier().getNamespace().equals("slashblade")) continue;
            require(!holder.id().identifier().getPath().startsWith("creative_tab/"),"empty catalog recipe visible");
            if (!(holder.value() instanceof BladeUpgradeRecipe recipe)) continue;
            count++;
            var layout=manager.createRecipeLayoutDrawable(category,holder,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
            require(layout.isPresent(),"JEI could not draw "+holder.id());
            var visible=manager.getRecipeIngredients(category,holder).getIngredients(RecipeIngredientRole.INPUT);
            require(!visible.isEmpty(),"JEI ingredient grid empty: "+holder.id());
            var outputs=recipe.display().getFirst().result().resolveForStacks(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(Minecraft.getInstance().level));
            var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,outputs.getFirst());
            require(manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(focus)).get().anyMatch(r -> r.id().equals(holder.id())),"named output recipe cannot be found: "+holder.id());
        }
        require(count==23,"missing direct upgrade recipes: "+count);
        long anvils=manager.createRecipeLookup(RecipeTypes.ANVIL).get().filter(r -> r.getUid()!=null && r.getUid().getNamespace().equals("slashblade")).count();
        require(anvils==186,"refining recipes missing: "+anvils);
        for(var material:List.of(mods.flammpfeil.slashblade.init.SBItems.proudsoul_tiny,mods.flammpfeil.slashblade.init.SBItems.proudsoul,mods.flammpfeil.slashblade.init.SBItems.proudsoul_ingot,mods.flammpfeil.slashblade.init.SBItems.proudsoul_sphere,mods.flammpfeil.slashblade.init.SBItems.proudsoul_crystal,mods.flammpfeil.slashblade.init.SBItems.proudsoul_trapezohedron)) {
            var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,new net.minecraft.world.item.ItemStack(material));
            require(manager.createRecipeLookup(RecipeTypes.ANVIL).limitFocus(List.of(focus)).get().anyMatch(r -> r.getUid()!=null && r.getUid().getNamespace().equals("slashblade")),"refining lookup missing for "+material);
        }
        require(manager.createRecipeLookup(RecipeTypes.SMITHING).get().noneMatch(r -> r.id().identifier().getNamespace().equals("slashblade") && r.id().identifier().getPath().startsWith("anvilcrafting/")),"barrier example leaked into JEI");
        SlashBlade.LOGGER.info("Recipe client verification PASSED: 31 blade variants, {} direct upgrades, {} anvil upgrades",count,anvils);
        return Map.of("status","passed","bladeVariants",31,"directUpgrades",count,"refiningRecipes",anvils,"scope","real JEI runtime, subtype identities, visible materials, layouts and named-output recipe lookups");
    }
    private static void show() {
        var mc=Minecraft.getInstance();var runtime=SlashBladeJeiPlugin.runtime();
        if(stage==0) { mc.setScreen(new IconScreen()); return; }
        if(stage==6) { mc.setScreen(new TooltipScreen()); return; }
        var manager=runtime.getRecipeManager();
        if(stage==5) {
            var recipes=manager.createRecipeLookup(RecipeTypes.ANVIL).get().filter(r -> r.getUid()!=null && r.getUid().getNamespace().equals("slashblade") && r.getUid().getPath().startsWith("refine/0/")).toList();
            runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(RecipeTypes.ANVIL),recipes,List.of());
        } else {
            String id=switch(stage) { case 1 -> "slashblade_white";case 2 -> "ruby";case 3 -> "fox_black";default -> "doutanuki"; };
            var recipes=manager.createRecipeLookup(RecipeTypes.CRAFTING).get().filter(r -> r.id().identifier().equals(SlashBlade.id("upgrades/"+id))).toList();
            runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(RecipeTypes.CRAFTING),recipes,List.of());
        }
    }
    private static final class TooltipScreen extends Screen {
        private final net.minecraft.world.item.ItemStack blade=BladeCatalog.blade("yuzukitukumo");
        TooltipScreen() {
            super(Component.literal("Resharped tooltip"));
            SBData.get(blade,ItemSlashBlade.BLADESTATE).ifPresent(s -> {s.setProudSoulCount(162);s.setKillCount(200);});
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xff18222e);
            g.text(font,"SlashBlade:Re / Resharped tooltip and item icon",25,25,0xffe8edf2,false);
            g.fakeItem(blade,75,70);g.setTooltipForNextFrame(font,blade,100,90);
        }
    }
    private static final class IconScreen extends Screen {
        IconScreen() { super(Component.literal("SlashBlade:Re — icon verification")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xff18222e);
            g.text(font,"SlashBlade:Re / 31 blade variants / actual GUI item renderer",25,25,0xffe8edf2,false);
            var items=BladeCatalog.items();
            for(int i=0;i<items.size();i++) {
                int x=30+(i%5)*128,y=65+(i/5)*52;var item=items.get(i);
                g.fill(x-1,y-1,x+17,y+17,0xff6b6b6b);g.fakeItem(item,x,y);
                g.text(font,item.getHoverName(),x+20,y+4,0xffffffff,false);
                g.pose().pushMatrix();g.pose().translate(x+30,y+18);g.pose().scale(2,2);
                g.fakeItem(item,0,0);g.pose().popMatrix();
            }
        }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    private BladeRecipeClientProbe() {}
}
