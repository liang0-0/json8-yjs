package com.ai.types.vo;

import com.ai.structs.AbstractStruct;
import com.ai.structs.GC;
import com.ai.structs.Skip;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.utils.codec.decoder.UpdateDecoder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.ai.structs.item.Item.readItemContent;

/**
 * 延迟结构读取器，用于按需读取更新中的结构体
 */
public class LazyStructReader {
    private Iterator<AbstractStruct> gen; // 结构体生成器
    public AbstractStruct curr;         // 当前结构体
    private boolean done;                // 是否读取完成
    private boolean filterSkips;         // 是否过滤Skip结构

    /**
     * 构造函数
     *
     * @param decoder     更新解码器
     * @param filterSkips 是否过滤Skip结构
     */
    public LazyStructReader(UpdateDecoder decoder, boolean filterSkips) {
        this.gen = lazyStructReaderGenerator(decoder);
        this.filterSkips = filterSkips;
        this.next(); // 预读取第一个结构体
    }

    /**
     * 读取下一个结构体
     *
     * @return 下一个结构体，可能为null表示结束
     */
    public AbstractStruct next() {
        // 过滤Skip结构（如果配置）
        do {
            this.curr = gen.hasNext() ? gen.next() : null;
        } while (filterSkips && curr != null && curr instanceof Skip);
        return curr;
    }

        /**
     * 生成结构体读取器
     *
     * @param decoder 更新解码器
     * @return 结构体迭代器
     */
    public static Iterator<AbstractStruct> lazyStructReaderGenerator(UpdateDecoder decoder) {
        List<AbstractStruct> structs = new ArrayList<>();
        int numOfStateUpdates = decoder.readVarUint();

        for (int i = 0; i < numOfStateUpdates; i++) {
            int numberOfStructs = decoder.readVarUint();
            int client = decoder.readClient();
            int clock = decoder.readVarUint();

            for (int j = 0; j < numberOfStructs; j++) {
                int info = decoder.readInfo();

                if (info == 10) { // Skip结构
                    int len = decoder.readVarUint();
                    structs.add(new Skip(new ID(client, clock), len));
                    clock += len;
                } else if ((info & 0b00011111) != 0) { // Item结构
                    boolean cantCopyParentInfo = (info & 0b11000000) == 0;
                    Item item = new Item(
                            new ID(client, clock),
                            null,
                            (info & 0b10000000) != 0 ? decoder.readLeftID() : null,
                            null,
                            (info & 0b01000000) != 0 ? decoder.readRightID() : null,
                            cantCopyParentInfo ? (decoder.readParentInfo() ? decoder.readString() : decoder.readLeftID()) : null,
                            cantCopyParentInfo && (info & 0b00100000) != 0 ? decoder.readString() : null,
                            readItemContent(decoder, info)
                    );
                    structs.add(item);
                    clock += item.length;
                } else { // GC结构
                    int len = decoder.readLen();
                    structs.add(new GC(new ID(client, clock), len));
                    clock += len;
                }
            }
        }
        return structs.iterator();
    }

}