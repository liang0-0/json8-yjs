package com.ai.myutils.observable;

public interface QuadConsumer<T, U, V, W> extends TriConsumer<T, U, V> {
    default void accept(T t, U u, V v) {}
    void accept(T t, U u, V v, W w);
}