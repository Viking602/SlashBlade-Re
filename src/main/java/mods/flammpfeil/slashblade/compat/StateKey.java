package mods.flammpfeil.slashblade.compat;

/** Typed lookup key for the legacy gameplay interfaces backed by modern storage. */
public record StateKey<T>(Class<T> type) {
    public static <T> StateKey<T> of(Class<T> type) { return new StateKey<>(type); }
}
