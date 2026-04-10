package ru.raidan.books.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Memoized {

    public static <T> Supplier<T> memoize(Supplier<T> delegate) {
        var ref = new AtomicReference<T>();
        return () -> {
            var value = ref.get();
            if (value == null) {
                value = ref.updateAndGet(cur -> cur == null
                        ? Objects.requireNonNull(delegate.get())
                        : cur);
            }
            return value;
        };
    }
}
