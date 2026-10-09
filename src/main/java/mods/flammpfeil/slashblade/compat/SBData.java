package mods.flammpfeil.slashblade.compat;

import com.google.common.collect.MapMaker;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import mods.flammpfeil.slashblade.capability.concentrationrank.*;
import mods.flammpfeil.slashblade.capability.inputstate.*;
import mods.flammpfeil.slashblade.capability.mobeffect.*;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;

/** Keeps the original gameplay state interfaces while using 26.1.2 persistence. */
public final class SBData {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, "slashblade");
    public static final Supplier<DataComponentType<CustomData>> BLADE_STATE = COMPONENTS.registerComponentType(
        "blade_state", builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC).ignoreSwapAnimation());
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "slashblade");
    public static final Supplier<AttachmentType<FreezeState>> SUPER_FREEZE = ATTACHMENTS.register("super_freeze", () ->
        AttachmentType.builder(() -> new FreezeState(-1L, 0)).serialize(FreezeState.CODEC)
            .sync(FreezeState.SYNC).build());
    public static final Supplier<AttachmentType<IInputState>> INPUT = ATTACHMENTS.register("inputstate", () ->
        AttachmentType.<IInputState>builder(InputState::new).serialize(serializer(
            () -> new InputStateCapabilityProvider(), InputStateCapabilityProvider::deserializeNBT,
            InputStateCapabilityProvider::getState, value -> new InputStateCapabilityProvider(value).serializeNBT())).build());
    public static final Supplier<AttachmentType<IMobEffectState>> EFFECT = ATTACHMENTS.register("mobeffect", () ->
        AttachmentType.<IMobEffectState>builder(MobEffectState::new).serialize(serializer(
            () -> new MobEffectCapabilityProvider(), MobEffectCapabilityProvider::deserializeNBT,
            MobEffectCapabilityProvider::getState, value -> new MobEffectCapabilityProvider(value).serializeNBT())).build());
    public static final Supplier<AttachmentType<IConcentrationRank>> RANK = ATTACHMENTS.register("concentration", () ->
        AttachmentType.<IConcentrationRank>builder(ConcentrationRank::new).serialize(serializer(
            () -> new ConcentrationRankCapabilityProvider(), ConcentrationRankCapabilityProvider::deserializeNBT,
            ConcentrationRankCapabilityProvider::getState, value -> new ConcentrationRankCapabilityProvider(value).serializeNBT())).build());

    private interface Reader<P> { void read(P provider, CompoundTag tag); }
    private static <T, P> IAttachmentSerializer<T> serializer(Supplier<P> factory, Reader<P> reader, Function<P,T> getter, Function<T,CompoundTag> writer) {
        return new IAttachmentSerializer<>() {
            public T read(IAttachmentHolder holder, ValueInput input) {
                P provider = factory.get();
                input.read("state", CompoundTag.CODEC).ifPresent(tag -> reader.read(provider, tag));
                return getter.apply(provider);
            }
            public boolean write(T value, ValueOutput output) {
                output.store("state", CompoundTag.CODEC, writer.apply(value));
                return true;
            }
        };
    }
    public static void register(IEventBus bus) { COMPONENTS.register(bus); ATTACHMENTS.register(bus); }

    // Guava weakKeys uses identity, so changing ItemStack components cannot change cache keys.
    // Values hold a weak reference too; discarded stacks cannot be retained by their state callback.
    private static final ConcurrentMap<ItemStack, BladeHandle> BLADES = new MapMaker().weakKeys().makeMap();
    private static final class BladeHandle {
        final WeakReference<ItemStack> stack;
        SlashBladeState state;
        CustomData seen;
        boolean loading;
        BladeHandle(ItemStack stack) { this.stack = new WeakReference<>(stack); load(stack); }
        synchronized void load(ItemStack item) {
            loading = true;
            state = new SlashBladeState();
            mods.flammpfeil.slashblade.init.BladeCatalog.initializeBase(item.getItem(),state);
            seen = item.getOrDefault(BLADE_STATE.get(), CustomData.EMPTY);
            if (!seen.isEmpty()) new BladeStateCapabilityProvider(state).deserializeNBT(seen.copyTag());
            state.setChangeListener(this::save);
            loading = false;
            if (seen.isEmpty()) save();
        }
        synchronized void save() {
            ItemStack item = stack.get();
            if (loading || item == null) return;
            seen = CustomData.of((CompoundTag)new BladeStateCapabilityProvider(state).serializeNBT());
            item.set(BLADE_STATE.get(), seen);
            item.set(net.minecraft.core.component.DataComponents.RARITY, state.getRarity());
            item.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE,state.getMaxDamage());
        }
        synchronized SlashBladeState get(ItemStack item) {
            if (!item.getOrDefault(BLADE_STATE.get(), CustomData.EMPTY).equals(seen)) load(item);
            return state;
        }
    }

    public static <T> LazyOptional<T> get(Object holder, StateKey<T> key) {
        Object value = null;
        if (holder instanceof ItemStack stack && key.type() == ISlashBladeState.class && stack.getItem() instanceof ItemSlashBlade) {
            value = BLADES.computeIfAbsent(stack, BladeHandle::new).get(stack);
        } else if (holder instanceof LivingEntity entity) {
            if (key.type() == IInputState.class) value = entity.getData(INPUT);
            else if (key.type() == IMobEffectState.class) value = entity.getData(EFFECT);
            else if (key.type() == IConcentrationRank.class) value = entity.getData(RANK);
        }
        T result = value == null ? null : key.type().cast(value);
        return LazyOptional.of(() -> result);
    }
    private SBData() {}
}
