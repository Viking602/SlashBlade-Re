package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

public class BlockPickCanceller {
    private static final class SingletonHolder {
        private static final BlockPickCanceller instance = new BlockPickCanceller();
    }
    public static BlockPickCanceller getInstance() {
        return BlockPickCanceller.SingletonHolder.instance;
    }
    private BlockPickCanceller(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onBlockPick(InputEvent.InteractionKeyMappingTriggered event){
        Minecraft minecraft=Minecraft.getInstance();
        if(event.isAttack() && minecraft.hitResult!=null && minecraft.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.MISS
                && minecraft.player!=null && !minecraft.player.isUsingItem()) {
            SBData.get(minecraft.player.getMainHandItem(),ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                        new mods.flammpfeil.slashblade.network.BladeAttackMessage(state.getUniqueId()));
                event.setSwingHand(false);
            });
        }
        if(!event.isPickBlock()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null) return;

        if(SBData.get(player.getMainHandItem(), ItemSlashBlade.BLADESTATE).isPresent()){
            event.setCanceled(true);
        }
    }
}
