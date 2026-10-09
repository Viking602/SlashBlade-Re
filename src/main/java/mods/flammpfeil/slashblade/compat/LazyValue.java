package mods.flammpfeil.slashblade.compat;

import java.util.function.Supplier;

/** Memoized value, used for model data that may only be loaded after client initialization. */
public final class LazyValue<T> implements Supplier<T> {
    private Supplier<T> factory;
    private T value;
    public LazyValue(Supplier<T> factory) { this.factory = factory; }
    @Override public synchronized T get() {
        if (factory != null) { value = factory.get(); factory = null; }
        return value;
    }
}
