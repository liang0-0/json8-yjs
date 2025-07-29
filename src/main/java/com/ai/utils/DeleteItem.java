package com.ai.utils;

import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.codec.encoder.UpdateEncoderV2;
import com.ai.utils.structstore.StructStore;

import java.util.*;
import java.util.function.Consumer;

import static com.ai.utils.structstore.StructStore.iterateStructs;

/**
 * 表示删除项的类，包含时钟和长度信息
 */
public class DeleteItem {
    public final int clock;
    public int len;

    /**
     * @param clock 时钟值
     * @param len 长度
     */
    public DeleteItem(int clock, int len) {
        this.clock = clock;
        this.len = len;
    }
}

