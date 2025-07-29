package com.ai.myutils.observable;

import java.util.function.Consumer;

public interface BinaryConsumer<T, U> extends Consumer<T> {
    default void accept(T t) {}
    void accept(T t, U u);
}
