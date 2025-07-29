package com.ai.utils.codec.decoder;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;

public abstract class DSDecoderV1 extends UpdateDecoder {
    public DSDecoderV1(Decoder decoder) {
        super(decoder);
    }

    public void resetDsCurVal() {}

    public int readDsClock() {
        return decoding.readVarUint(this.restDecoder);
    }

    public int readDsLen() {
        return readVarUint();
    }
}


