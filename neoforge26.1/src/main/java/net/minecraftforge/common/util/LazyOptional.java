package net.minecraftforge.common.util;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LazyOptional<T> {

    private static final LazyOptional<?> EMPTY = new LazyOptional<>(null);

    private final Supplier<T> supplier;
    private Optional<T> cached;

    private LazyOptional(Supplier<T> supplier) {
        this.supplier = supplier;
        this.cached = Optional.empty();
    }

    public static <T> LazyOptional<T> of(Supplier<T> supplier) {
        return new LazyOptional<>(supplier);
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>) EMPTY;
    }

    public <R> LazyOptional<R> cast() {
        return of(() -> (R) resolve().orElseThrow(NoSuchElementException::new));
    }

    public Optional<T> resolve() {
        if (supplier == null) {
            return Optional.empty();
        }
        if (cached.isEmpty()) {
            cached = Optional.ofNullable(supplier.get());
        }
        return cached;
    }

    public T orElse(T other) {
        return resolve().orElse(other);
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return resolve().orElseThrow(exceptionSupplier);
    }

    public void ifPresent(Consumer<? super T> consumer) {
        resolve().ifPresent(consumer);
    }
}
