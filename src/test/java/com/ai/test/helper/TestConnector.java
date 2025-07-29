package com.ai.test.helper;

import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试连接器，管理多个测试Y实例
 */
public class TestConnector {
    public final Set<TestYInstance> allConns = new LinkedHashSet<>(); // 所有连接
    public final Set<TestYInstance> onlineConns = new LinkedHashSet<>(); // 在线连接
    private final Random prng; // 随机数生成器

    public TestConnector(Random gen) {
        this.prng = gen;
    }

    /**
     * 创建新的Y实例
     *
     * @param clientID 客户端ID
     * @return 新创建的Y实例
     */
    public TestYInstance createY(int clientID) {
        return new TestYInstance(this, clientID);
    }

    /**
     * 随机处理一条消息
     *
     * @return 是否处理了消息
     */
    public boolean flushRandomMessage() {
        // 获取有未处理消息的在线连接
        List<TestYInstance> conns = onlineConns.stream().filter(conn -> !conn.receiving.isEmpty()).collect(Collectors.toList());

        if (!conns.isEmpty()) {
            // 随机选择一个接收方
            TestYInstance receiver = conns.get(prng.nextInt(conns.size()));

            // 随机选择一个发送方和消息
            List<Map.Entry<TestYInstance, List<int[]>>> entries = new ArrayList<>(receiver.receiving.entrySet());
            Map.Entry<TestYInstance, List<int[]>> entry = entries.get(prng.nextInt(entries.size()));

            TestYInstance sender = entry.getKey();
            List<int[]> messages = entry.getValue();

            if (!messages.isEmpty()) {
                int[] m = messages.remove(0);
                if (messages.isEmpty()) {
                    receiver.receiving.remove(sender);
                }

                if (m == null) {
                    return this.flushRandomMessage();
                }

                Encoder encoder = encoding.createEncoder();
                // 处理消息
                syncProtocol.readSyncMessage(decoding.createDecoder(m), encoder, receiver, this);
                if (encoding.length(encoder) > 0) {
                    sender._receive(encoding.toUint8Array(encoder), receiver);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 处理所有消息
     *
     * @return 是否处理了任何消息
     */
    public boolean flushAllMessages() {
        boolean didSomething = false;
        while (flushRandomMessage()) {
            didSomething = true;
        }
        return didSomething;
    }

    /**
     * 重新连接所有实例
     */
    public void reconnectAll() {
        for (TestYInstance conn : allConns) {
            conn.connect();
        }
    }

    /**
     * 断开所有连接
     */
    public void disconnectAll() {
        for (TestYInstance conn : allConns) {
            conn.disconnect();
        }
    }

    /**
     * 同步所有实例
     */
    public void syncAll() {
        reconnectAll();
        flushAllMessages();
    }

    /**
     * 随机断开一个连接
     *
     * @return 是否成功断开
     */
    public boolean disconnectRandom() {
        if (onlineConns.isEmpty()) {
            return false;
        }
        List<TestYInstance> list = new ArrayList<>(onlineConns);
        list.get(prng.nextInt(list.size())).disconnect();
        return true;
    }

    /**
     * 随机重连一个连接
     *
     * @return 是否成功重连
     */
    public boolean reconnectRandom() {
        List<TestYInstance> reconnectable = new ArrayList<>();
        for (TestYInstance conn : allConns) {
            if (!onlineConns.contains(conn)) {
                reconnectable.add(conn);
            }
        }

        if (reconnectable.isEmpty()) {
            return false;
        }

        reconnectable.get(prng.nextInt(reconnectable.size())).connect();
        return true;
    }
}
