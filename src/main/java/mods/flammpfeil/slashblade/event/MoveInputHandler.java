package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.compat.SBItemData;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.compat.StateKey;
import com.google.gson.*;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.network.MoveCommandMessage;
import mods.flammpfeil.slashblade.network.NetworkManager;
import mods.flammpfeil.slashblade.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.EnumSet;

public class MoveInputHandler {

    public static final StateKey<IInputState> INPUT_STATE = StateKey.of(IInputState.class);

    public static final String LAST_CHANGE_TIME = "SB_LAST_CHANGE_TIME";

    public static boolean checkFlag(int data, int flags){
        return (data & flags) == flags;
    }


    @SubscribeEvent()
    static public void onPlayerPostTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){

        if(!(event.getEntity() instanceof LocalPlayer)) return;

        LocalPlayer player = (LocalPlayer)event.getEntity();

        for (ItemStack held : player.getInventory()) {
            if (held.getItem() instanceof ItemSlashBlade blade) blade.tickInventory(held, player.level(), player, held == player.getMainHandItem());
        }

        EnumSet<InputCommand> commands = EnumSet.noneOf(InputCommand.class);

        if(player.input.keyPresses.forward())
            commands.add(InputCommand.FORWARD);
        if(player.input.keyPresses.backward())
            commands.add(InputCommand.BACK);
        if(player.input.keyPresses.left())
            commands.add(InputCommand.LEFT);
        if(player.input.keyPresses.right())
            commands.add(InputCommand.RIGHT);

        if(player.input.keyPresses.shift())
            commands.add(InputCommand.SNEAK);

        if(Minecraft.getInstance().options.keySprint.isDown())
            commands.add(InputCommand.SPRINT);

        if(Minecraft.getInstance().options.keyJump.isDown()){
            commands.add(InputCommand.JUMP);
        }

/*
        if((player.movementInput.sneak && SlashBlade.SneakForceLockOn)
                || CoreProxyClient.lockon.isKeyDown())
            message.activeTag += MoveCommandMessage.SNEAK;

        
        if(CoreProxyClient.camera.isKeyDown())
            message.activeTag += MoveCommandMessage.CAMERA;

        if(CoreProxyClient.styleaction.isKeyDown())
            message.activeTag += MoveCommandMessage.STYLE;
*/

        if(Minecraft.getInstance().options.keyUse.isDown())
            commands.add(InputCommand.R_DOWN);
        if(Minecraft.getInstance().options.keyAttack.isDown())
            commands.add(InputCommand.L_DOWN);

        if(Minecraft.getInstance().options.keyPickItem.isDown())
            commands.add(InputCommand.M_DOWN);



        if(Minecraft.getInstance().options.keySaveHotbarActivator.isDown())
            commands.add(InputCommand.SAVE_TOOLBAR);

        EnumSet<InputCommand> old = SBData.get(player, INPUT_STATE)
                .map((state)->state.getCommands())
                .orElseGet(()->EnumSet.noneOf(InputCommand.class));

        Level worldIn = player.level();

        /*
        if(player.movementInput.forwardKeyDown &&  (0 < (player.getPersistentData().getIntOr(KEY, 0) & MoveCommandMessage.SNEAK)))
            player.getPersistentData().putLong("SB.MCS.F",currentTime);
        if(player.movementInput.backKeyDown &&  (0 < (player.getPersistentData().getIntOr(KEY, 0) & MoveCommandMessage.SNEAK)))
            player.getPersistentData().putLong("SB.MCS.B",currentTime);
        */

        boolean doCopy = player.isCreative();

        if(doCopy && old.contains(InputCommand.SAVE_TOOLBAR) && !commands.contains(InputCommand.SAVE_TOOLBAR)){
            ItemStack stack = player.getMainHandItem();

            JsonObject ret = new JsonObject();

            String str = "";
            if(KeyModifier.SHIFT.isActive(KeyConflictContext.UNIVERSAL)){
                str = AdvancementBuilder.getAdvancementJsonStr(stack);
            }else{

                ItemStack exported = stack.copy();
                if (KeyModifier.ALT.isActive(KeyConflictContext.UNIVERSAL)) {
                    AnvilCraftingRecipe recipe = new AnvilCraftingRecipe();
                    recipe.setResult(player.getOffhandItem());
                    SBItemData.put(exported, "RequiredBlade", recipe.writeNBT());
                }
                ret = SBItemData.toJson(exported);
                if (KeyModifier.CONTROL.isActive(KeyConflictContext.UNIVERSAL)) {
                    JsonObject expected = new JsonObject();
                    SBData.get(stack, ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                        expected.addProperty("translationKey", state.getTranslationKey());
                        if (state.isBroken()) expected.addProperty("isBroken", 1);
                    });
                    JsonObject predicates = new JsonObject(); predicates.add("slashblade:blade", expected);
                    JsonObject item = new JsonObject();
                    item.addProperty("items", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    item.add("predicates", predicates); ret.add("CriteriaItem", item);
                }

                Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                str = GSON.toJson(ret);
            }

            Minecraft.getInstance().keyboardHandler.setClipboard(str);
        }

        long currentTime = worldIn.getGameTime();
        boolean doSend = !old.equals(commands);

        if(doSend){
            SBData.get(player, INPUT_STATE)
                    .ifPresent((state)->{
                        commands.forEach(c->{
                            if(!old.contains(c))
                                state.getLastPressTimes().put(c, currentTime);
                        });

                        state.getCommands().clear();
                        state.getCommands().addAll(commands);
                    });
            MoveCommandMessage msg = new MoveCommandMessage();
            msg.command = EnumSetConverter.convertToInt(commands);
            ClientPacketDistributor.sendToServer(msg);
        }
    }
}
