package mods.flammpfeil.slashblade.entity;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ItemLike;

public class BladeStandEntity extends ItemFrame implements IEntityWithComplexSpawn {

    public Item currentType = null;
    public ItemStack currentTypeStack = ItemStack.EMPTY;

    public BladeStandEntity(EntityType<? extends BladeStandEntity> p_i50224_1_, Level p_i50224_2_) {
        super(p_i50224_1_, p_i50224_2_);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity) {
        return super.getAddEntityPacket(entity);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        CompoundTag compound = new CompoundTag();
        writeBladeData(compound);
        output.store("slashblade:blade_stand_entity", CompoundTag.CODEC, compound);
    }
    private void writeBladeData(CompoundTag compound) {

        String standTypeStr;
        if(this.currentType != null){
            standTypeStr = BuiltInRegistries.ITEM.getKey(this.currentType).toString();
        }else{
            standTypeStr = "";
        }
        compound.putString("StandType", standTypeStr);

        compound.putByte("Pose", (byte)this.getPose().ordinal());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        readBladeData(input.read("slashblade:blade_stand_entity", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
    private void readBladeData(CompoundTag compound) {

        this.currentType = BuiltInRegistries.ITEM.getValue(Identifier.parse(compound.getStringOr("StandType", "")));

        this.setPose(Pose.values()[compound.getByteOr("Pose", (byte)0) % Pose.values().length]);
    }

    @Override
    public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        this.writeBladeData(tag);
        buffer.writeNbt(tag);
    }

    @Override
    public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf additionalData) {
        CompoundTag tag = additionalData.readNbt();
        this.readBladeData(tag);
    }

    public static BladeStandEntity createInstanceFromPos(Level worldIn, BlockPos placePos, Direction dir, Item type) {
        BladeStandEntity e = new BladeStandEntity(SlashBlade.RegistryEvents.BladeStand, worldIn);

        e.setPos(placePos.getCenter());
        e.setDirection(dir);
        e.currentType = type;

        return e;
    }

    public static BladeStandEntity createInstance(net.minecraft.network.protocol.game.ClientboundAddEntityPacket spawnEntity, Level world) {
        return new BladeStandEntity(SlashBlade.RegistryEvents.BladeStand, world);
    }

    @Nullable
    @Override
    public ItemEntity spawnAtLocation(net.minecraft.server.level.ServerLevel level, ItemLike iip) {
        if(iip == Items.ITEM_FRAME){
            if(this.currentType == null || this.currentType == Items.AIR)
                return null;

            iip = this.currentType;
        }
        return super.spawnAtLocation(level, iip);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
        InteractionResult result = InteractionResult.PASS;
        if(!this.level().isClientSide() && hand == InteractionHand.MAIN_HAND){
            ItemStack itemstack = player.getItemInHand(hand);
            if(player.isShiftKeyDown() && !this.getItem().isEmpty()){
                Pose current = this.getPose();
                int newIndex = (current.ordinal() + 1) % Pose.values().length;
                this.setPose(Pose.values()[newIndex]);
                result = InteractionResult.SUCCESS;
            }else if((!itemstack.isEmpty() && itemstack.getItem() instanceof ItemSlashBlade)
                        || (itemstack.isEmpty() && !this.getItem().isEmpty())){

                if(this.getItem().isEmpty()){
                    if (!this.isRemoved()) {
                        this.setItem(itemstack);
                        if (!player.getAbilities().instabuild) {
                            itemstack.shrink(1);
                        }
                        this.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F, 1.0F);
                        result = InteractionResult.SUCCESS;
                    }
                }else{
                    ItemStack displayed = this.getItem().copy();

                    this.setItem(itemstack);
                    player.setItemInHand(hand, displayed);

                    this.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
                    result = InteractionResult.SUCCESS;

                }

            }else {
                this.playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
                result = InteractionResult.SUCCESS;
            }
        }
        return result;
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount) {
        if(source.getEntity() instanceof net.minecraft.world.entity.boss.wither.WitherBoss && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            var blade=getItem();
            if(blade.getItem() instanceof ItemSlashBlade && mods.flammpfeil.slashblade.init.BladeCatalog.baseItemIdentity(blade).equals("slashblade:slashblade")
                && mods.flammpfeil.slashblade.init.BladeCatalog.canonicalName(mods.flammpfeil.slashblade.compat.SBData.get(blade,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new).getTranslationKey()).isEmpty()) {
                setItem(mods.flammpfeil.slashblade.init.BladeCatalog.blade("koseki"));return true;
            }
        }
        return super.hurtServer(level,source,amount);
    }

    protected ItemStack getFrameItemStack() {
        return new ItemStack(currentType);
    }

}
