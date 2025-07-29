package com.ai.protocol.vo;

import lombok.Data;

import java.util.List;

// 事件类定义
@Data
public class ChangeEvent {
    public final List<Integer> added;
    public final List<Integer> updated;
    public final List<Integer> removed;

    public ChangeEvent(List<Integer> added, List<Integer> updated, List<Integer> removed) {
        this.added = added;
        this.updated = updated;
        this.removed = removed;
    }
}

