package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.Nullable;

/** A side-local animation clock and a shared world-time freeze deadline. */
public record FreezeState(long until, int age) {
    // Older saves only contain "until". Entity age normally starts at zero on load.
    public static final MapCodec<FreezeState> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.fieldOf("until").forGetter(FreezeState::until),
            Codec.INT.optionalFieldOf("age", 0).forGetter(FreezeState::age)
    ).apply(instance, FreezeState::new));

    public static FreezeState capture(Entity entity, long until) {
        return new FreezeState(until, entity.tickCount);
    }

    // Keep the existing single VarLong wire format. Each client captures its own
    // animation age once, so freezing never jumps to the server's animation phase.
    public static final AttachmentSyncHandler<FreezeState> SYNC = new AttachmentSyncHandler<>() {
        @Override public void write(RegistryFriendlyByteBuf buf, FreezeState value, boolean initialSync) {
            buf.writeVarLong(value.until());
        }

        @Override public FreezeState read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf,
                                          @Nullable FreezeState previous) {
            long until = buf.readVarLong();
            if (previous != null && previous.until() == until) return previous;
            return new FreezeState(until, holder instanceof Entity entity ? entity.tickCount : 0);
        }
    };
}
