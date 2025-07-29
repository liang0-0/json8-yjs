package com.ai.utils.undo;

import com.ai.types.arraytype.AbstractType;
import com.ai.utils.YEvent;

import java.util.List;
import java.util.Map;

public class StackItemEvent {
    public final StackItem stackItem;
    public final Object origin;
    public final String type; // "undo" or "redo"
    public final Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes;

    public StackItemEvent(StackItem stackItem, Object origin, String type,
                          Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes) {
        this.stackItem = stackItem;
        this.origin = origin;
        this.type = type;
        this.changedParentTypes = changedParentTypes;
    }
}
