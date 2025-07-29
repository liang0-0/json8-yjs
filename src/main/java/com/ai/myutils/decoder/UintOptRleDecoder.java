package com.ai.myutils.decoder;

import com.ai.myutils.binary;

import static com.ai.myutils.decoder.decoding.readVarInt;
import static com.ai.myutils.decoder.decoding.readVarUint;

public class UintOptRleDecoder extends Decoder {
    private int s;
    public int count;

    public UintOptRleDecoder(int[] uint8Array) {
        super(uint8Array);
        this.s = 0;
        this.count = 0;
    }

    public int read() {
        if (this.count == 0) {
            this.s = readVarInt(this);
            boolean isNegative = (this.arr[this.pos-1] & binary.BIT7) > 0;
            this.count = 1;
            if (isNegative) {
                this.s = -this.s;
                this.count = readVarUint(this) + 2;
            }
        }
        this.count--;
        return this.s;
    }
}
