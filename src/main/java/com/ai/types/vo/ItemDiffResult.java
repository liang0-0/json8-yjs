package com.ai.types.vo;

import com.ai.structs.item.Item;

/**
 * 获取项结果内部类
 */
public class ItemDiffResult {
    public final Item item;  // 找到的结构项
    public final int diff;            // 时钟偏移量

    public ItemDiffResult(Item item, int diff) {
        this.item = item;
        this.diff = diff;
    }
}