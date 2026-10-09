package mods.flammpfeil.slashblade.entity;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jetbrains.annotations.Nullable;

public class PlacePreviewEntity extends ItemFrame implements IEntityWithComplexSpawn {

    public Item currentType = null;
    public ItemStack currentTypeStack = ItemStack.EMPTY;

    public PlacePreviewEntity(EntityType<? extends PlacePreviewEntity> p_i50224_1_, Level p_i50224_2_) {
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
        output.store("slashblade:place_preview_entity", CompoundTag.CODEC, compound);
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
        readBladeData(input.read("slashblade:place_preview_entity", CompoundTag.CODEC).orElseGet(CompoundTag::new));
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

    public static PlacePreviewEntity createInstanceFromPos(Level worldIn, BlockPos placePos, Direction dir, Item type) {
        PlacePreviewEntity e = new PlacePreviewEntity(SlashBlade.RegistryEvents.PlacePreview, worldIn);

        e.setPos(placePos.getCenter());
        e.setDirection(dir);
        e.currentType = type;

        return e;
    }

    public static PlacePreviewEntity createInstance(net.minecraft.network.protocol.game.ClientboundAddEntityPacket spawnEntity, Level world) {
        return new PlacePreviewEntity(SlashBlade.RegistryEvents.PlacePreview, world);
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
        if(!this.level().isClientSide()){
            ItemStack itemstack = player.getItemInHand(hand);
            if(player.isShiftKeyDown() && !this.getItem().isEmpty()){
                Pose current = this.getPose();
                int newIndex = (current.ordinal() + 1) % Pose.values().length;
                this.setPose(Pose.values()[newIndex]);
                result = InteractionResult.SUCCESS;
            }else if((!itemstack.isEmpty() && itemstack.getItem() instanceof ItemSlashBlade)
                    || (itemstack.isEmpty() && !this.getItem().isEmpty())){

                if(this.getItem().isEmpty()){
                    result = super.interact(player, hand, location);
                }else{
                    ItemStack displayed = this.getItem().copy();

                    this.setItem(ItemStack.EMPTY);
                    result = super.interact(player, hand, location);

                    player.setItemInHand(hand, displayed);
                }

            }else {
                this.playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
                result = InteractionResult.SUCCESS;
            }
        }
        return result;
    }

    private BlockState tile = Blocks.SAND.defaultBlockState();

    public BlockState getBlockState() {
        return this.tile;
    }
}
