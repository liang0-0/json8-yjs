package com.ai.myutils.decoder;

import static com.ai.myutils.decoder.decoding.*;

public class RleIntDiffDecoder extends Decoder {
    private long s;
    private int count;

    public RleIntDiffDecoder(int[] uint8Array, long start) {
        super(uint8Array);
        this.s = start;
        this.count = 0;
    }

    public long read() {
        if (this.count == 0) {
            this.s += readVarInt(this);
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
