package com.ai.utils.codec.encoder;

import com.ai.myutils.encoder.encoding;
import com.ai.types.ID;
import com.alibaba.fastjson.JSON;

public class UpdateEncoderV1 extends DSEncoderV1 {
    public UpdateEncoderV1() {
        super();
    }

    public void writeLeftID(ID id) {
        encoding.writeVarUint(this.restEncoder, id.client);
        encoding.writeVarUint(this.restEncoder, id.clock);
    }

    public void writeRightID(ID id) {
        encoding.writeVarUint(this.restEncoder, id.client);
        encoding.writeVarUint(this.restEncoder, id.clock);
    }

    public void writeClient(int client) {
        encoding.writeVarUint(this.restEncoder, client);
    }

    @Override
    public void writeInfo(int info) {
        encoding.writeUint8(this.restEncoder, info);

    }

    public void writeString(String s) {
        encoding.writeVarString(this.restEncoder, s);
    }

    public void writeParentInfo(boolean isYKey) {
        encoding.writeVarUint(this.restEncoder, isYKey ? 1 : 0);
    }

    public void writeTypeRef(int info) {
        encoding.writeVarUint(this.restEncoder, info);
    }


    public void writeLen(int len) {
        encoding.writeVarUint(this.restEncoder, len);
    }

    public void writeAny(Object any) {
        encoding.writeAny(this.restEncoder, any);
    }

    public void writeBuf(int[] buf) {
        encoding.writeVarUint8Array(this.restEncoder, buf);
    }

    public void writeJSON(Object embed) {
        encoding.writeVarString(this.restEncoder, JSON.toJSONString(embed));
    }

    public void writeKey(String key) {
        encoding.writeVarString(this.restEncoder, key);
    }

}
