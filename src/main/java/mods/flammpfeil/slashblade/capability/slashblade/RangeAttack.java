package mods.flammpfeil.slashblade.capability.slashblade;

import com.google.common.collect.Maps;
import mods.flammpfeil.slashblade.util.RegistryBase;
import net.minecraft.resources.Identifier;

import java.util.Map;

public class RangeAttack extends RegistryBase<RangeAttack> {
    static Map<Identifier, RangeAttack> registry = Maps.newHashMap();

    @Override
    public Map<Identifier, RangeAttack> getRegistry() {
        return RangeAttack.registry;
    }

    public static final RangeAttack NONE = new RangeAttack(BaseInstanceName);

    RangeAttack(String name) {
        super(name);
    }

    @Override
    public String getPath() {
        return "rangeattack";
    }

    @Override
    public RangeAttack getNone() {
        return NONE;
    }
}
