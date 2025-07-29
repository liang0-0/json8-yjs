package com.ai.utils.codec.encoder;

import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;

public abstract class DSEncoderV2 extends UpdateEncoder {
    protected int dsCurrVal;

    public DSEncoderV2() {
        super();
        this.dsCurrVal = 0;
    }

    @Override
    public int[] toUint8Array() {
        return encoding.toUint8Array(this.restEncoder);
    }

    public void resetDsCurVal() {
        this.dsCurrVal = 0;
    }

    public void writeDsClock(int clock) {
        int diff = clock - this.dsCurrVal;
        this.dsCurrVal = clock;
        encoding.writeVarUint(this.restEncoder, diff);
    }

    public void writeDsLen(int len) {
        if (len == 0) {
            throw new RuntimeException("Unexpected case");
        }
        encoding.writeVarUint(this.restEncoder, len - 1);
        this.dsCurrVal += len;
    }

//    public abstract void writeVarUint(Encoder encoder, int value);
//
//    public abstract void writeUint8(byte value);
//
//    public abstract void writeVarString(String value);
//
//    public abstract void writeVarUint8Array(Encoder encoder, int[] value);
//
//    public abstract void writeAny(Object value);
}
