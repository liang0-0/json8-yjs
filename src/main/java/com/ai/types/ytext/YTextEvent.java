package com.ai.types.ytext;

import com.ai.utils.Transaction;
import com.ai.utils.YEvent;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

// 辅助类和接口
public class YTextEvent extends YEvent<YText> {
    public boolean childListChanged;
    public Set<String> keysChanged;

    public YTextEvent(YText target, Transaction transaction, Set<String> subs) {
        super(target, transaction);
        this.childListChanged = false;
        this.keysChanged = new LinkedHashSet<>();
        subs.forEach(sub -> {
            if (sub == null) {
                this.childListChanged = true;
            } else {
                this.keysChanged.add(sub);
            }
        });
    }
}
