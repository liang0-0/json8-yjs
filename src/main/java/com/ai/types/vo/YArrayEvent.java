package com.ai.types.vo;

import com.ai.types.YArray;
import com.ai.utils.Transaction;
import com.ai.utils.YEvent;

/**
 * YArray事件类
 * @param <T> 数组元素类型
 */
public class YArrayEvent<T> extends YEvent<YArray<T>> {
    public YArrayEvent(YArray<T> target, Transaction transaction) {
        super(target, transaction);
    }
}