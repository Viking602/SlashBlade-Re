package mods.flammpfeil.slashblade.compat;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A lazily initialized value used by the original combo and model caches. */
public final class LazyOptional<T> {
    private Supplier<? extends T> supplier;
    private T value;
    private boolean resolved;
    private LazyOptional(Supplier<? extends T> supplier) { this.supplier = supplier; }
    public static <T> LazyOptional<T> of(Supplier<? extends T> supplier) { return new LazyOptional<>(supplier); }
    public static <T> LazyOptional<T> empty() { return of(() -> null); }
    public Optional<T> resolve() {
        if (!resolved) { value = supplier.get(); resolved = true; supplier = null; }
        return Optional.ofNullable(value);
    }
    public void invalidate() { value = null; resolved = true; supplier = null; }
    public boolean isPresent() { return resolve().isPresent(); }
    public void ifPresent(Consumer<? super T> action) { resolve().ifPresent(action); }
    public Optional<T> filter(Predicate<? super T> predicate) { return resolve().filter(predicate); }
    public <R> Optional<R> map(Function<? super T, ? extends R> mapper) { return resolve().map(mapper); }
    public T orElse(T fallback) { return resolve().orElse(fallback); }
    public T orElseGet(Supplier<? extends T> fallback) { return resolve().orElseGet(fallback); }
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> error) throws X { return resolve().orElseThrow(error); }
    @SuppressWarnings("unchecked") public <R> LazyOptional<R> cast() { return (LazyOptional<R>) this; }
}
