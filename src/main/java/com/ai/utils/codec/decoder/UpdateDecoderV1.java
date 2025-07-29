package com.ai.utils.codec.decoder;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.types.ID;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Arrays;

public class UpdateDecoderV1 extends DSDecoderV1 {
    public UpdateDecoderV1(Decoder decoder) {
        super(decoder);
    }

    public ID readLeftID() {
        return new ID(readVarUint(), readVarUint());
    }

    public ID readRightID() {
        return new ID(readVarUint(), readVarUint());
    }

    public int readClient() {
        return readVarUint();
    }

    public int readInfo() {
        return readUint8();
    }

    public String readString() {
        return readVarString();
    }

    public boolean readParentInfo() {
        return readVarUint() == 1;
    }

    public int readTypeRef() {
        return readVarUint();
    }

    public int readLen() {
        return readVarUint();
    }

    public Object readAny() {
        return decoding.readAny(this.restDecoder);
    }

    public int[] readBuf() {
        int[] data = readVarUint8Array();
        return Arrays.copyOf(data, data.length); // 创建数组的副本
    }

    public Object readJSON() {
        String text = readVarString();
        if ("null".equals(text)) {
            return null;
        }
        if (Strings.CS.containsAny(text, "true", "false")) {
            return Boolean.valueOf(text);
        }
        if (!text.startsWith("{") && !text.startsWith("[")) {
            text = StringUtils.removeStart(text, "\"");
            text = StringUtils.removeEnd(text, "\"");
            return text;
        }
        return JSONObject.parseObject(text);
    }

    public String readKey() {
        return readVarString();
    }
}
