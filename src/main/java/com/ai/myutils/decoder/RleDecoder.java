package com.ai.myutils.decoder;

import java.util.function.Function;

import static com.ai.myutils.decoder.decoding.hasContent;
import static com.ai.myutils.decoder.decoding.readVarUint;

public class RleDecoder<T> extends Decoder {
    private final Function<Decoder, T> reader;
    private T s;
    private int count;

    public RleDecoder(int[] uint8Array, Function<Decoder, T> reader) {
        super(uint8Array);
        this.reader = reader;
        this.s = null;
        this.count = 0;
    }

    public T read() {
        if (this.count == 0) {
            this.s = this.reader.apply(this);
            if (hasContent(this)) {
                this.count = readVarUint(this) + 1;
            } else {
                this.count = -1;
            }
        }
        this.count--;
        return this.s;
    }
}
