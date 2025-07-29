package com.ai.protocol;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.myutils.observable.ObservableV2;
import com.ai.protocol.vo.ChangeEvent;
import com.ai.protocol.vo.MetaClientState;
import com.ai.protocol.vo.UpdateEvent;
import com.ai.utils.Doc;
import com.alibaba.fastjson.JSON;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.util.Collections.*;

/**
 * Awareness类实现了一个简单的共享状态协议，用于非持久性数据（如光标位置、用户名、状态等）
 */
public class Awareness extends ObservableV2 {
    public static final long outdatedTimeout = 30000;
    private final Doc doc;
    private final int clientID;
    @Getter
    private final Map<Integer, Map<String, Object>> states = new ConcurrentHashMap<>();
    public final Map<Integer, MetaClientState> meta = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> checkTask;

    public Awareness(Doc doc) {
        this.doc = doc;
        this.clientID = doc.clientID;

        // 设置定期检查超时的任务
        this.checkTask = scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis() / 1000;
            // 更新本地状态时钟
            if (getLocalState() != null &&
                    (outdatedTimeout / 2 <= now - meta.get(clientID).lastUpdated)) {
                setLocalState(getLocalState());
            }

            // 检查超时的远程客户端
            List<Integer> remove = new ArrayList<>();
            meta.forEach((clientId, metaState) -> {
                if (clientId != clientID &&
                        outdatedTimeout <= now - metaState.lastUpdated &&
                        states.containsKey(clientId)) {
                    remove.add(clientId);
                }
            });

            if (!remove.isEmpty()) {
                removeAwarenessStates(this, remove, "timeout");
            }
        }, outdatedTimeout / 10, outdatedTimeout / 10, TimeUnit.MILLISECONDS);

        doc.<Doc>on("destroy", (o) -> destroy());
        setLocalState(new HashMap<>());
    }

    public void destroy() {
        emit("destroy", this);
        setLocalState(null);
        super.destroy();
        checkTask.cancel(true);
        scheduler.shutdown();
    }

    public Map<String, Object> getLocalState() {
        return states.get(clientID);
    }

    public void setLocalState(Map<String, Object> state) {
        MetaClientState currLocalMeta = meta.get(clientID);
        int clock = (currLocalMeta == null) ? 0 : currLocalMeta.clock + 1;
        Map<String, Object> prevState = states.get(clientID);

        if (state == null) {
            states.remove(clientID);
        } else {
            states.put(clientID, state);
        }

        meta.put(clientID, new MetaClientState(clock, System.currentTimeMillis() / 1000));

        List<Integer> added = new ArrayList<>();
        List<Integer> updated = new ArrayList<>();
        List<Integer> filteredUpdated = new ArrayList<>();
        List<Integer> removed = new ArrayList<>();

        if (state == null) {
            removed.add(clientID);
        } else if (prevState == null) {
            added.add(clientID);
        } else {
            updated.add(clientID);
            if (!deepEquals(prevState, state)) {
                filteredUpdated.add(clientID);
            }
        }

        if (!added.isEmpty() || !filteredUpdated.isEmpty() || !removed.isEmpty()) {
            emit("change", new ChangeEvent(added, filteredUpdated, removed), "local");
        }
        emit("update", new UpdateEvent(added, updated, removed), "local");
    }

    public void setLocalStateField(String field, Object value) {
        Map<String, Object> state = getLocalState();
        if (state != null) {
            Map<String, Object> newState = new HashMap<>(state);
            newState.put(field, value);
            setLocalState(newState);
        }
    }

    private boolean deepEquals(Map<String, Object> a, Map<String, Object> b) {
        // 实现深度比较逻辑
        return Objects.deepEquals(a, b);
    }


    /**
     * 移除客户端状态
     */
    public static void removeAwarenessStates(Awareness awareness, List<Integer> clients, String origin) {
        List<Integer> removed = new ArrayList<>();
        for (int clientID : clients) {
            if (awareness.states.containsKey(clientID)) {
                awareness.states.remove(clientID);
                if (clientID == awareness.clientID) {
                    MetaClientState curMeta = awareness.meta.get(clientID);
                    awareness.meta.put(clientID, new MetaClientState(curMeta.clock + 1, System.currentTimeMillis() / 1000));
                }
                removed.add(clientID);
            }
        }

        if (!removed.isEmpty()) {
            awareness.emit("change", new ChangeEvent(emptyList(), emptyList(), removed), origin);
            awareness.emit("update", new UpdateEvent(emptyList(), emptyList(), removed), origin);
        }
    }

    /**
     * 编码awareness更新
     */
    public static int[] encodeAwarenessUpdate(Awareness awareness, List<Integer> clients) {
        return encodeAwarenessUpdate(awareness, clients, awareness.states);
    }

    public static int[] encodeAwarenessUpdate(Awareness awareness, List<Integer> clients,
                                               Map<Integer, Map<String, Object>> states) {
        Encoder encoder = encoding.createEncoder();
        encoding.writeVarUint(encoder, clients.size());

        for (int clientID : clients) {
            Map<String, Object> state = states.getOrDefault(clientID, null);
            int clock = awareness.meta.get(clientID).clock;

            encoding.writeVarUint(encoder, clientID);
            encoding.writeVarUint(encoder, clock);
            encoding.writeVarString(encoder, JSON.toJSONString(state));
        }

        return encoding.toUint8Array(encoder);
    }

    /**
     * 修改awareness更新内容
     */
    public static int[] modifyAwarenessUpdate(int[] update, Function<Object, Object> modify) {
        Decoder decoder = decoding.createDecoder(update);
        Encoder encoder = encoding.createEncoder();

        int len = decoding.readVarUint(decoder);
        encoding.writeVarUint(encoder, len);

        for (int i = 0; i < len; i++) {
            int clientID = decoding.readVarUint(decoder);
            int clock = decoding.readVarUint(decoder);
            Object state = JSON.parse(decoding.readVarString(decoder));

            Object modifiedState = modify.apply(state);

            encoding.writeVarUint(encoder, clientID);
            encoding.writeVarUint(encoder, clock);
            encoding.writeVarString(encoder, JSON.toJSONString(modifiedState));
        }

        return encoding.toUint8Array(encoder);
    }

    /**
     * 应用awareness更新
     */
    public static void applyAwarenessUpdate(Awareness awareness, int[] update, String origin) {
        Decoder decoder = decoding.createDecoder(update);
        long timestamp = System.currentTimeMillis() / 1000;

        List<Integer> added = new ArrayList<>();
        List<Integer> updated = new ArrayList<>();
        List<Integer> filteredUpdated = new ArrayList<>();
        List<Integer> removed = new ArrayList<>();

        int len = decoding.readVarUint(decoder);
        for (int i = 0; i < len; i++) {
            int clientID = decoding.readVarUint(decoder);
            int clock = decoding.readVarUint(decoder);
            Map<String, Object> state = JSON.parseObject(decoding.readVarString(decoder));

            MetaClientState clientMeta = awareness.meta.get(clientID);
            Map<String, Object> prevState = awareness.states.get(clientID);
            int currClock = (clientMeta == null) ? 0 : clientMeta.clock;

            if (currClock < clock || (currClock == clock && state == null && awareness.states.containsKey(clientID))) {
                if (state == null) {
                    // 不允许远程客户端移除本地状态
                    if (clientID == awareness.clientID && awareness.getLocalState() != null) {
                        clock++; // 增加时钟表明客户端仍然存在
                    } else {
                        awareness.states.remove(clientID);
                    }
                } else {
                    awareness.states.put(clientID, state);
                }

                awareness.meta.put(clientID, new MetaClientState(clock, timestamp));

                if (clientMeta == null && state != null) {
                    added.add(clientID);
                } else if (clientMeta != null && state == null) {
                    removed.add(clientID);
                } else if (state != null) {
                    if (!awareness.deepEquals(state, prevState)) {
                        filteredUpdated.add(clientID);
                    }
                    updated.add(clientID);
                }
            }
        }

        if (!added.isEmpty() || !filteredUpdated.isEmpty() || !removed.isEmpty()) {
            awareness.emit("change", new ChangeEvent(added, filteredUpdated, removed), origin);
        }
        if (!added.isEmpty() || !updated.isEmpty() || !removed.isEmpty()) {
            awareness.emit("update", new UpdateEvent(added, updated, removed), origin);
        }
    }
}