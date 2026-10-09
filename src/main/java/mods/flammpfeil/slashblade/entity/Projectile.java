package mods.flammpfeil.slashblade.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public abstract class Projectile extends net.minecraft.world.entity.projectile.Projectile {
    private static final EntityDataAccessor<Integer> OWNERID = SynchedEntityData.defineId(Projectile.class, EntityDataSerializers.INT);

    protected Projectile(EntityType<? extends net.minecraft.world.entity.projectile.Projectile> p_37248_, Level p_37249_) {
        super(p_37248_, p_37249_);
    }

    @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
        return false; // Summoned blades are deflected explicitly by ArrowReflector, and have no health.
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(OWNERID, -1);
    }

    @Nullable
    @Override
    public Entity getOwner() {
        // Server ownership is a persistent UUID reference. Numeric entity IDs only
        // serve the client spawn/tracking path and change after a world reload.
        if (!this.level().isClientSide()) {
            Entity owner = super.getOwner();
            if (owner != null && entityData.get(OWNERID) != owner.getId()) entityData.set(OWNERID, owner.getId());
            return owner;
        }
        int id = entityData.get(OWNERID);
        if (id < 0) return null;
        Entity owner = level().getEntity(id);
        if (owner != null) super.setOwner(owner);
        return owner;
    }

    @Override
    public void setOwner(@Nullable Entity p_37263_) {
        if(p_37263_ != null)
            this.entityData.set(OWNERID, p_37263_.getId());
        else
            this.entityData.set(OWNERID, -1);

        super.setOwner(p_37263_);
    }
}
