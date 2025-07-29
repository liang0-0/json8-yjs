package com.ai.types.vo;

import com.ai.types.arraytype.AbstractType;

public class AbsolutePosition {
    public final AbstractType type;
    public final int index;
    public final int assoc;

    public AbsolutePosition(AbstractType type, int index, int assoc) {
        this.type = type;
        this.index = index;
        this.assoc = assoc;
    }
}