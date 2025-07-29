package com.ai.protocol;

import com.ai.Y;
import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.utils.Doc;

public class SyncProtocol {
    // 消息类型常量
    public static final int MESSAGE_YJS_SYNC_STEP_1 = 0;
    public static final int MESSAGE_YJS_SYNC_STEP_2 = 1;
    public static final int MESSAGE_YJS_UPDATE = 2;

    /**
     * 创建同步步骤1消息（基于当前共享文档状态）
     * @param encoder 编码器
     * @param doc Y文档实例
     */
    public static void writeSyncStep1(Encoder encoder, Doc doc) {
        // 写入消息类型
        encoding.writeVarUint(encoder, MESSAGE_YJS_SYNC_STEP_1);
        // 编码状态向量并写入
        int[] stateVector = Y.encodeStateVector(doc);
        encoding.writeVarUint8Array(encoder, stateVector);
    }

    /**
     * 创建同步步骤2消息
     * @param encoder 编码器
     * @param doc Y文档实例
     * @param encodedStateVector 已编码的状态向量（可选）
     */
    public static void writeSyncStep2(Encoder encoder, Doc doc, int[] encodedStateVector) {
        // 写入消息类型
        encoding.writeVarUint(encoder, MESSAGE_YJS_SYNC_STEP_2);
        // 编码状态更新并写入
        int[] stateUpdate = Y.encodeStateAsUpdate(doc, encodedStateVector);
        encoding.writeVarUint8Array(encoder, stateUpdate);
    }

    /**
     * 读取SyncStep1消息并回复SyncStep2
     * @param decoder 接收到的消息解码器
     * @param encoder 回复消息编码器
     * @param doc Y文档实例
     */
    public static void readSyncStep1(Decoder decoder, Encoder encoder, Doc doc) {
        // 读取状态向量并生成步骤2响应
        int[] stateVector = decoding.readVarUint8Array(decoder);
        writeSyncStep2(encoder, doc, stateVector);
    }

    /**
     * 读取并应用结构体和删除集到Y实例
     * @param decoder 解码器
     * @param doc Y文档实例
     * @param transactionOrigin 事务来源
     */
    public static void readSyncStep2(Decoder decoder, Doc doc, Object transactionOrigin) {
        try {
            // 读取更新数据并应用到文档
            int[] update = decoding.readVarUint8Array(decoder);
            Y.applyUpdate(doc, update, transactionOrigin);
        } catch (Exception error) {
            // 捕获并记录事件处理程序抛出的错误
            System.err.println("处理Yjs更新时发生错误: " + error);
        }
    }

    /**
     * 写入更新消息
     * @param encoder 编码器
     * @param update 更新数据
     */
    public static void writeUpdate(Encoder encoder, int[] update) {
        // 写入消息类型和更新数据
        encoding.writeVarUint(encoder, MESSAGE_YJS_UPDATE);
        encoding.writeVarUint8Array(encoder, update);
    }

    /**
     * 读取并应用更新（复用readSyncStep2的实现）
     */
    public static void readUpdate(Decoder decoder, Doc doc, Object transactionOrigin) {
        readSyncStep2(decoder, doc, transactionOrigin);
    }

    /**
     * 读取同步消息并根据类型处理
     * @param decoder 消息解码器
     * @param encoder 回复编码器（可为空）
     * @param doc Y文档实例
     * @param transactionOrigin 事务来源
     * @return 处理的消息类型
     * @throws RuntimeException 未知消息类型时抛出
     */
    public static int readSyncMessage(Decoder decoder, Encoder encoder, Doc doc, Object transactionOrigin) {
        int messageType = decoding.readVarUint(decoder);
        switch (messageType) {
            case MESSAGE_YJS_SYNC_STEP_1:
                readSyncStep1(decoder, encoder, doc);
                break;
            case MESSAGE_YJS_SYNC_STEP_2:
                readSyncStep2(decoder, doc, transactionOrigin);
                break;
            case MESSAGE_YJS_UPDATE:
                readUpdate(decoder, doc, transactionOrigin);
                break;
            default:
                throw new RuntimeException("未知的消息类型: " + messageType);
        }
        return messageType;
    }
}