package com.ai.types.vo;

/**
 * 客户端结构体信息
 */
public class ClientStruct {
    public int written;           // 写入数量
    public int[] restEncoder;    // 编码数据

    public ClientStruct(int written, int[] restEncoder) {
        this.written = written;
        this.restEncoder = restEncoder;
    }
}