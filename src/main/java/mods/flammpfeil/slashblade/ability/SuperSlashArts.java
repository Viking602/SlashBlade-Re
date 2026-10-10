package mods.flammpfeil.slashblade.ability;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.specialattack.SlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/** Resharped's held-sprint charge and per-blade Super art dispatch. */
public final class SuperSlashArts {
    public static final int CHARGE_TICKS = 20;
    private static final SuperSlashArts INSTANCE = new SuperSlashArts();
    public static SuperSlashArts getInstance() { return INSTANCE; }

    public static boolean eligible(ItemStack stack) {
        var types = SwordType.from(stack);
        return SBData.get(stack, ItemSlashBlade.BLADESTATE).filter(state -> !state.isBroken()
                && state.getDamage() <= 0 && !state.isSealed()
                && types.contains(SwordType.Bewitched) && types.contains(SwordType.FiercerEdge)).isPresent();
    }

    private static boolean stillCharging(ServerPlayer player, long pressTime) {
        var input = player.getData(SBData.INPUT);
        var commands = input.getCommands();
        return commands.contains(InputCommand.SPRINT)
                && (!InputCommand.anyMatch(commands, InputCommand.move) || !commands.contains(InputCommand.SNEAK))
                && input.getLastPressTime(InputCommand.SPRINT) == pressTime;
    }

    @SubscribeEvent public void onInputChange(InputCommandEvent event) {
        if (event.getOld().contains(InputCommand.SPRINT) || !event.getCurrent().contains(InputCommand.SPRINT)) return;
        long pressTime = event.getState().getLastPressTime(InputCommand.SPRINT);
        var scheduler = event.getState().getScheduler();
        scheduler.schedule("sendPartical", pressTime + 5, (entity, queue, now) -> {
            if (!(entity instanceof ServerPlayer player) || !stillCharging(player, pressTime)
                    || !player.onGround() || !eligible(player.getMainHandItem())) return;
            var random = player.getRandom();
            for (int i = 0; i < 32; i++) {
                double x = random.nextFloat() * 2 - 1, y = random.nextFloat() * 2 - 1, z = random.nextFloat() * 2 - 1;
                if (x * x + y * y + z * z > 1) continue;
                player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(x / 4),
                        player.getY(.5 + y / 4), player.getZ(z / 4), 0, x, y + .2, z, 1);
            }
        });
        scheduler.schedule("chargeSuperSA", pressTime + CHARGE_TICKS, (entity, queue, now) -> {
            if (entity instanceof ServerPlayer player && stillCharging(player, pressTime)) releaseSSA(player);
        });
    }

    public static void releaseSSA(ServerPlayer player) {
        var stack = player.getMainHandItem();
        if (!player.onGround() || !eligible(stack)) return;
        var state = SBData.get(stack, ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        // The eligibility check guarantees a full blade. Half its durability cannot
        // break it; this vanilla path retains Unbreaking, creative and Unbreakable
        // handling while bypassing our suppression of duplicate weapon-component wear.
        stack.hurtWithoutBreaking(stack.getMaxDamage() / 2, player);
        var current = state.resolvCurrentComboStateTicks(player).getValue();
        var next = state.getSlashArts().doArts(SlashArts.ArtsType.Super, player);
        if (next != ComboState.NONE && next != current && current.getPriority() > next.getPriority())
            state.updateComboSeq(player, next);
    }
    private SuperSlashArts() {}
}
