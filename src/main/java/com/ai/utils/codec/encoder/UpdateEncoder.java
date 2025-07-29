package com.ai.utils.codec.encoder;

import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.types.ID;

public abstract class UpdateEncoder {
    public Encoder restEncoder;

    public UpdateEncoder() {
        this.restEncoder = encoding.createEncoder();
    }

    public abstract int[] toUint8Array();

    public abstract void writeInfo(int info);

    public abstract void writeLen(int len);

    public abstract void writeAny(Object value);

    public abstract void writeBuf(int[] content);

    public abstract void writeString(String guid);

    public abstract void writeJSON(Object embed);

    public abstract void writeKey(String key);

    public abstract void writeTypeRef(int yArrayRefID);

    public void resetDsCurVal() {
        // no op
    };

    public abstract void writeDsClock(int clock);

    public abstract void writeDsLen(int len);

    public abstract void writeLeftID(ID origin);

    public abstract void writeRightID(ID rightOrigin);

    public abstract void writeParentInfo(boolean b);

    public abstract void writeClient(int client);

}
