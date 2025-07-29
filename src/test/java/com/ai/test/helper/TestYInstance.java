package com.ai.test.helper;

import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.utils.Doc;
import com.ai.utils.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ai.test.helper.TestHelper.broadcastMessage;

/**
 * 测试Y实例，模拟一个客户端连接
 */
public class TestYInstance extends Doc {
    public final TestConnector tc; // 测试连接器
    public Map<TestYInstance, List<int[]>> receiving = new HashMap<>(); // 接收队列
    public final List<int[]> updates = new ArrayList<>(); // 更新记录
    public final int userID;

    public TestYInstance(TestConnector testConnector, int clientID) {
        this.tc = testConnector;
        this.userID = clientID;
        testConnector.allConns.add(this);

        // 监听本地模型更新
        this.<int[], Object, Doc, Transaction>on(TestHelper.enc.updateEventName, (update, origin, doc, transaction) -> {
            if (origin != testConnector) {
                Encoder encoder = encoding.createEncoder();
                syncProtocol.writeUpdate(encoder, update);
                broadcastMessage(this, encoding.toUint8Array(encoder));
            }
            this.updates.add(update);
        });

        this.connect();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        this.receiving = new HashMap<>();
        this.tc.onlineConns.remove(this);
    }

    /**
     * 连接并初始化同步
     */
    public void connect() {
        if (!this.tc.onlineConns.contains(this)) {
            this.tc.onlineConns.add(this);
            Encoder encoder = encoding.createEncoder();
            syncProtocol.writeSyncStep1(encoder, this);
            broadcastMessage(this, encoding.toUint8Array(encoder));

            // 与其他在线实例同步
            for (TestYInstance remoteYInstance : this.tc.onlineConns) {
                if (remoteYInstance != this) {
                    Encoder encoder1 = encoding.createEncoder();
                    syncProtocol.writeSyncStep1(encoder1, remoteYInstance);
                    this._receive(encoding.toUint8Array(encoder), remoteYInstance);
                }
            }
        }
    }

    /**
     * 接收消息（暂存到接收队列）
     *
     * @param message      消息内容
     * @param remoteClient 发送方
     */
    public void _receive(int[] message, TestYInstance remoteClient) {
        receiving.computeIfAbsent(remoteClient, k -> new ArrayList<>()).add(message);
    }
}
