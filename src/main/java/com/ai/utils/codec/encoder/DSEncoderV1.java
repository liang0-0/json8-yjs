package com.ai.utils.codec.encoder;

import com.ai.myutils.encoder.encoding;

public abstract class DSEncoderV1 extends UpdateEncoder {

    public DSEncoderV1() {
        super();
    }

    @Override
    public int[] toUint8Array() {
        return encoding.toUint8Array(this.restEncoder);
    }

    public void writeDsClock(int clock) {
        encoding.writeVarUint(this.restEncoder, clock);
    }

    public void writeDsLen(int len) {
        encoding.writeVarUint(this.restEncoder, len);
    }
}
