package com.ai.types.vo;

import java.util.Map;

/**
 * 更新元数据容器类
 */
public class UpdateMeta {
    public final Map<Integer, Integer> from;
    public final Map<Integer, Integer> to;

    public UpdateMeta(Map<Integer, Integer> from, Map<Integer, Integer> to) {
        this.from = from;
        this.to = to;
    }
}