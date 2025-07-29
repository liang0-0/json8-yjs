package com.ai.utils.codec.decoder;

import com.ai.myutils.decoder.*;
import com.ai.types.ID;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UpdateDecoderV2 extends DSDecoderV2 {
    private List<String> keys;
    private IntDiffOptRleDecoder keyClockDecoder;
    private UintOptRleDecoder clientDecoder;
    private IntDiffOptRleDecoder leftClockDecoder;
    private IntDiffOptRleDecoder rightClockDecoder;
    private RleDecoder<Integer> infoDecoder;
    private StringDecoder stringDecoder;
    private RleDecoder<Integer> parentInfoDecoder;
    private UintOptRleDecoder typeRefDecoder;
    private UintOptRleDecoder lenDecoder;

    public UpdateDecoderV2(Decoder decoder) {
        super(decoder);
        this.keys = new ArrayList<>();
        decoding.readVarUint(decoder); // read feature flag - currently unused
        this.keyClockDecoder = new IntDiffOptRleDecoder(decoding.readVarUint8Array(decoder));
        this.clientDecoder = new UintOptRleDecoder(decoding.readVarUint8Array(decoder));
        this.leftClockDecoder = new IntDiffOptRleDecoder(decoding.readVarUint8Array(decoder));
        this.rightClockDecoder = new IntDiffOptRleDecoder(decoding.readVarUint8Array(decoder));
        this.infoDecoder = new RleDecoder<>(decoding.readVarUint8Array(decoder), decoding::readUint8);
        this.stringDecoder = new StringDecoder(decoding.readVarUint8Array(decoder));
        this.parentInfoDecoder = new RleDecoder<>(decoding.readVarUint8Array(decoder), decoding::readUint8);
        this.typeRefDecoder = new UintOptRleDecoder(decoding.readVarUint8Array(decoder));
        this.lenDecoder = new UintOptRleDecoder(decoding.readVarUint8Array(decoder));
    }

    public ID readLeftID() {
        return new ID(clientDecoder.read(), (int) leftClockDecoder.read());
    }

    public ID readRightID() {
        return new ID(clientDecoder.read(), (int) rightClockDecoder.read());
    }

    public int readClient() {
        return clientDecoder.read();
    }

    public int readInfo() {
        return infoDecoder.read();
    }

    public String readString() {
        return stringDecoder.read();
    }

    public boolean readParentInfo() {
        return parentInfoDecoder.read() == 1;
    }

    public int readTypeRef() {
        return typeRefDecoder.read();
    }

    public int readLen() {
        return lenDecoder.read();
    }

    @Override
    public Object readAny() {
        return decoding.readAny(this.restDecoder);
    }

    public int[] readBuf() {
        return decoding.readVarUint8Array(this.restDecoder);
    }

    public Object readJSON() {
        return decoding.readAny(this.restDecoder);
    }

    public String readKey() {
        int keyClock = (int) keyClockDecoder.read();
        if (keyClock < keys.size()) {
            return keys.get(keyClock);
        } else {
            String key = stringDecoder.read();
            keys.add(key);
            return key;
        }
    }
}