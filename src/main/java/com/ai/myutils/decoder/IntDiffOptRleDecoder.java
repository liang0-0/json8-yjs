package com.ai.myutils.decoder;

import static com.ai.myutils.decoder.decoding.readVarInt;
import static com.ai.myutils.decoder.decoding.readVarUint;

public class IntDiffOptRleDecoder extends Decoder {
    private long s;
    private int count;
    private long diff;

    public IntDiffOptRleDecoder(int[] uint8Array) {
        super(uint8Array);
        this.s = 0;
        this.count = 0;
        this.diff = 0;
    }

    public long read() {
        if (this.count == 0) {
            long diff = readVarInt(this);
            boolean hasCount = (diff & 1) != 0;
            this.diff = diff / 2;  // 相当于右移1位
            this.count = 1;
            if (hasCount) {
                this.count = (int)readVarUint(this) + 2;
            }
        }
        this.s += this.diff;
        this.count--;
        return this.s;
    }
}
