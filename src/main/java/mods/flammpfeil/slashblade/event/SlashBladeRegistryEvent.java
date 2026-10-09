package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

public abstract class SlashBladeRegistryEvent extends Event {
    private final SlashBladeDefinition definition;

    public SlashBladeRegistryEvent(SlashBladeDefinition definition) {
        this.definition = definition;
    }

    public SlashBladeDefinition getSlashBladeDefinition() {
        return definition;
    }

    public static class Pre extends SlashBladeRegistryEvent implements ICancellableEvent {
        public Pre(SlashBladeDefinition definition) {
            super(definition);
        }

    }

    public static class Post extends SlashBladeRegistryEvent {
        private final ItemStack blade;

        public Post(SlashBladeDefinition definition, ItemStack blade) {
            super(definition);
            this.blade = blade;
        }

        public ItemStack getBlade() {
            return blade;
        }

    }
}
