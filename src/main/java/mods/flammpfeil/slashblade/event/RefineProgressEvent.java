package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.bus.api.ICancellableEvent;

import javax.annotation.Nullable;

public class RefineProgressEvent extends SlashBladeEvent implements ICancellableEvent {
    private final AnvilUpdateEvent originalEvent;
    private int materialCost;
    private int levelCost;
    private final int costResult;
    private int refineResult;

    public RefineProgressEvent(ItemStack blade, ISlashBladeState state, int materialCost,
                               int levelCost, int costResult, int refineResult, AnvilUpdateEvent originalEvent) {
        super(blade, state);
        this.materialCost = materialCost;
        this.levelCost = levelCost;
        this.costResult = costResult;
        this.refineResult = refineResult;
        this.originalEvent = originalEvent;
    }

    public @Nullable AnvilUpdateEvent getOriginalEvent() {
        return originalEvent;
    }

    public int getMaterialCost() {
        return materialCost;
    }

    public int setMaterialCost(int materialCost) {
        this.materialCost = materialCost;
        return this.materialCost;
    }

    public int getLevelCost() {
        return levelCost;
    }

    public int setLevelCost(int levelCost) {
        this.levelCost = levelCost;
        return this.levelCost;
    }

    public int getCostResult() {
        return costResult;
    }

    public int getRefineResult() {
        return refineResult;
    }

    public int setRefineResult(int refineResult) {
        this.refineResult = refineResult;
        return this.refineResult;
    }
}
