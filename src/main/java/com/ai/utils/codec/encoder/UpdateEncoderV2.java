package com.ai.utils.codec.encoder;

import com.ai.myutils.encoder.*;
import com.ai.types.ID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.ai.myutils.Printer.print;
import static com.ai.myutils.encoder.encoding.writeVarUint8Array;

public class UpdateEncoderV2<T> extends DSEncoderV2 {
    private Map<String, Integer> keyMap;
    private int keyClock;
    private IntDiffOptRleEncoder keyClockEncoder;
    private UintOptRleEncoder clientEncoder;
    private IntDiffOptRleEncoder leftClockEncoder;
    private IntDiffOptRleEncoder rightClockEncoder;
    private RleEncoder infoEncoder;
    private StringEncoder stringEncoder;
    private RleEncoder parentInfoEncoder;
    private UintOptRleEncoder typeRefEncoder;
    private UintOptRleEncoder lenEncoder;

    public UpdateEncoderV2() {
        super();
        this.keyMap = new HashMap<>();
        this.keyClock = 0;
        this.keyClockEncoder = new IntDiffOptRleEncoder();
        this.clientEncoder = new UintOptRleEncoder();
        this.leftClockEncoder = new IntDiffOptRleEncoder();
        this.rightClockEncoder = new IntDiffOptRleEncoder();
        this.infoEncoder = new RleEncoder(encoding::writeUint8);
        this.stringEncoder = new StringEncoder();
        this.parentInfoEncoder = new RleEncoder(encoding::writeUint8);
        this.typeRefEncoder = new UintOptRleEncoder();
        this.lenEncoder = new UintOptRleEncoder();
    }

    @Override
    public int[] toUint8Array() {
        Encoder encoder = encoding.createEncoder();
        encoding.writeVarUint(encoder, 0);
        encoding.writeVarUint8Array(encoder, this.keyClockEncoder.toUint8Array());
        encoding.writeVarUint8Array(encoder, this.clientEncoder.toUint8Array());
        encoding.writeVarUint8Array(encoder, this.leftClockEncoder.toUint8Array());
        encoding.writeVarUint8Array(encoder, this.rightClockEncoder.toUint8Array());
        encoding.writeVarUint8Array(encoder, encoding.toUint8Array(this.infoEncoder));
        encoding.writeVarUint8Array(encoder, this.stringEncoder.toUint8Array());
        encoding.writeVarUint8Array(encoder, encoding.toUint8Array(this.parentInfoEncoder));
        encoding.writeVarUint8Array(encoder, this.typeRefEncoder.toUint8Array());
        encoding.writeVarUint8Array(encoder, this.lenEncoder.toUint8Array());
        encoding.writeUint8Array(encoder, encoding.toUint8Array(this.restEncoder));
        return encoding.toUint8Array(encoder);
    }

    public void writeLeftID(ID id) {
        this.clientEncoder.write(id.client);
        this.leftClockEncoder.write(id.clock);
    }

    public void writeRightID(ID id) {
        this.clientEncoder.write(id.client);
        this.rightClockEncoder.write(id.clock);
    }

    public void writeClient(int client) {
        this.clientEncoder.write(client);
    }

    public void writeInfo(int info) {
        this.infoEncoder.write(info);
    }

    public void writeString(String s) {
        this.stringEncoder.write(s);
    }

    public void writeParentInfo(boolean isYKey) {
        this.parentInfoEncoder.write(isYKey ? 1 : 0);
    }

    public void writeTypeRef(int info) {
        this.typeRefEncoder.write(info);
    }

    public void writeLen(int len) {
        this.lenEncoder.write(len);
    }

    public void writeAny(Object any) {
        encoding.writeAny(this.restEncoder, any);
    }

    public void writeBuf(int[] buf) {
        writeVarUint8Array(this.restEncoder, buf);
    }

    public void writeJSON(Object embed) {
        encoding.writeAny(this.restEncoder, embed);
    }

    public void writeKey(String key) {
        Integer clock = this.keyMap.get(key);
        if (clock == null) {
            this.keyClockEncoder.write(this.keyClock++);
            this.stringEncoder.write(key);
        } else {
            this.keyClockEncoder.write(clock);
        }
    }
}