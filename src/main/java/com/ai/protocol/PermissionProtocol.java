package com.ai.protocol;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.utils.Doc;

public class PermissionProtocol {
    // 消息类型常量
    public static final int MESSAGE_PERMISSION_DENIED = 0;

    /**
     * 写入权限拒绝消息
     * @param encoder 编码器
     * @param reason 拒绝原因
     */
    public static void writePermissionDenied(Encoder encoder, String reason) {
        // 写入消息类型
        encoding.writeVarUint(encoder, MESSAGE_PERMISSION_DENIED);
        // 写入拒绝原因
        encoding.writeVarString(encoder, reason);
    }

    /**
     * 权限拒绝处理器接口
     */
    public interface PermissionDeniedHandler {
        void handle(Doc doc, String reason);
    }

    /**
     * 读取认证消息
     * @param decoder 解码器
     * @param doc Y文档实例
     * @param handler 权限拒绝处理器
     */
    public static void readAuthMessage(Decoder decoder, Doc doc, PermissionDeniedHandler handler) {
        int messageType = decoding.readVarUint(decoder);
        if (messageType == MESSAGE_PERMISSION_DENIED) {// 读取拒绝原因并调用处理器
            String reason = decoding.readVarString(decoder);
            handler.handle(doc, reason);
        } else {
            throw new RuntimeException("未知的认证消息类型: " + messageType);
        }
    }

}