package com.ai.myutils.observable;

public interface TriConsumer<T, U, V> extends BinaryConsumer<T, U> {
    default void accept(T t, U u) {}
    void accept(T t, U u, V v);
}
