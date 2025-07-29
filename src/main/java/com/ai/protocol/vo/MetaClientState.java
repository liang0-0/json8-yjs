package com.ai.protocol.vo;

/**
 * 客户端元数据状态
 */
public class MetaClientState {
    public int clock;
    public long lastUpdated; // Unix时间戳

    public MetaClientState(int clock, long lastUpdated) {
        this.clock = clock;
        this.lastUpdated = lastUpdated;
    }
}
