package com.ai.types.vo;

import com.ai.structs.AbstractStruct;
import com.ai.utils.DeleteSet;

import java.util.List;

/**
 * 解码结果容器类
 */
public class DecodedUpdate {
    public final List<AbstractStruct> structs;
    public final DeleteSet ds;

    public DecodedUpdate(List<AbstractStruct> structs, DeleteSet ds) {
        this.structs = structs;
        this.ds = ds;
    }
}