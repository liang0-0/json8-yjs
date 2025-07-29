package com.ai.types.vo;

import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * 延迟结构写入器，用于按需写入结构体
 */
public class LazyStructWriter {
    public int currClient;       // 当前处理的客户端ID
    private int startClock;       // 起始时钟
    public int written;          // 已写入数量
    public UpdateEncoder encoder; // 编码器
    public List<ClientStruct> clientStructs; // 客户端结构体列表

    /**
     * 构造函数
     *
     * @param encoder 更新编码器
     */
    public LazyStructWriter(UpdateEncoder encoder) {
        this.encoder = encoder;
        this.clientStructs = new ArrayList<>();
    }
}