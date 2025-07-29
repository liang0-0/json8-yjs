package com.ai.myutils.encoder;

@FunctionalInterface
public interface EncoderFunction<T> {
    void apply(Encoder encoder, T value);
}
