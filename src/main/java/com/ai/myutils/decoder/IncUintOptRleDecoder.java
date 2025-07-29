package com.ai.myutils.decoder;

import com.ai.myutils.binary;

import static com.ai.myutils.decoder.decoding.readVarInt;
import static com.ai.myutils.decoder.decoding.readVarUint;

public class IncUintOptRleDecoder extends Decoder {
    private long s;
    private int count;

    public IncUintOptRleDecoder(int[] uint8Array) {
        super(uint8Array);
        this.s = 0;
        this.count = 0;
    }

    public long read() {
        if (this.count == 0) {
            this.s = readVarInt(this);
            boolean isNegative = (this.arr[this.pos] & binary.BIT7) > 0;  // Java中不能直接用<0判断负数
            this.count = 1;
            if (isNegative) {
                this.s = -this.s;
                this.count = (int)readVarUint(this) + 2;
            }
        }
        this.count--;
        return this.s++;  // 关键区别：返回当前值后自增
    }
}
