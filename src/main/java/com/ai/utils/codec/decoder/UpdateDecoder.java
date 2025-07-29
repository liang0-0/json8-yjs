package com.ai.utils.codec.decoder;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.types.ID;

public abstract class UpdateDecoder {
    public Decoder restDecoder;
    protected int dsCurrVal;

    public UpdateDecoder(Decoder decoder) {
        this.restDecoder = decoder;
        this.dsCurrVal = 0;
    }

    public int readVarUint() {
        return decoding.readVarUint(this.restDecoder);
    }

    public int readUint8() {
        return decoding.readUint8(this.restDecoder);
    }

    public String readVarString() {
        return decoding.readVarString(this.restDecoder);
    }

    public int[] readVarUint8Array() {
        return decoding.readVarUint8Array(this.restDecoder);
    }

    public abstract Object readAny();

    public abstract int readLen();

    public abstract int[] readBuf();

    public abstract String readString();

    public abstract Object readJSON();

    public abstract String readKey();

    public abstract int readTypeRef();

    public abstract void resetDsCurVal();

    public abstract int readDsClock();

    public abstract int readDsLen();

    public abstract int readClient();

    public abstract int readInfo();

    public abstract ID readLeftID();

    public abstract ID readRightID();

    public abstract boolean readParentInfo();
}
