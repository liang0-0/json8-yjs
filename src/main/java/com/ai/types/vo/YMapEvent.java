package com.ai.types.vo;

import com.ai.types.YMap;
import com.ai.types.arraytype.AbstractType;
import com.ai.utils.Transaction;
import com.ai.utils.YEvent;

import java.util.Set;

/**
 * YMap事件类
 * @param <T> Map值类型
 */
public class YMapEvent<T extends AbstractType<?>> extends YEvent<T> {
    // 变更的键集合
    public final Set<String> keysChanged;

    public YMapEvent(T target, Transaction transaction, Set<String> keysChanged) {
        super(target, transaction);
        this.keysChanged = keysChanged;
    }
}
