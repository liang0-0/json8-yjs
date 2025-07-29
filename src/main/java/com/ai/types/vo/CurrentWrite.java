package com.ai.types.vo;

import com.ai.structs.AbstractStruct;

public class CurrentWrite {
    public AbstractStruct struct;
    public int offset;

    public CurrentWrite(AbstractStruct struct, int offset) {
        this.struct = struct;
        this.offset = offset;
    }
}