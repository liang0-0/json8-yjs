package com.ai.utils.codec.decoder;

import com.ai.myutils.decoder.Decoder;

public abstract class DSDecoderV2 extends UpdateDecoder {
    public DSDecoderV2(Decoder decoder) {
        super(decoder);
    }

    public void resetDsCurVal() {
        this.dsCurrVal = 0;
    }

    public int readDsClock() {
        this.dsCurrVal += readVarUint();
        return this.dsCurrVal;
    }

    public int readDsLen() {
        int diff = readVarUint() + 1;
        this.dsCurrVal += diff;
        return diff;
    }
}
