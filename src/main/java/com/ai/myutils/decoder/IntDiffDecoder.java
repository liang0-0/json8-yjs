package com.ai.myutils.decoder;

import static com.ai.myutils.decoder.decoding.readVarInt;

public class IntDiffDecoder extends Decoder {
    private long s;

    public IntDiffDecoder(int[] uint8Array, long start) {
        super(uint8Array);
        this.s = start;
    }

    public long read() {
        this.s += readVarInt(this);
        return this.s;
    }
}
