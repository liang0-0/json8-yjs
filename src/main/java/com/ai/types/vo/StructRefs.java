package com.ai.types.vo;

import com.ai.structs.AbstractStruct;

import java.util.List;

public class StructRefs {
    public int i;
    public AbstractStruct[] refs;

    public StructRefs(int i, AbstractStruct[] refs) {
        this.i = i;
        this.refs = refs;
    }
}
