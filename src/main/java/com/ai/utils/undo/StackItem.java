package com.ai.utils.undo;

import com.ai.utils.DeleteSet;

import java.util.HashMap;
import java.util.Map;

public class StackItem {
    public DeleteSet deletions;
    public DeleteSet insertions;
    public final Map<Object, Object> meta;

    public StackItem(DeleteSet deletions, DeleteSet insertions) {
        this.deletions = deletions;
        this.insertions = insertions;
        this.meta = new HashMap<>();
    }
}
