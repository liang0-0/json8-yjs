package com.ai.protocol.vo;

import java.util.List;

public class UpdateEvent {
    public final List<Integer> added;
    public final List<Integer> updated;
    public final List<Integer> removed;

    public UpdateEvent(List<Integer> added, List<Integer> updated, List<Integer> removed) {
        this.added = added;
        this.updated = updated;
        this.removed = removed;
    }
}
