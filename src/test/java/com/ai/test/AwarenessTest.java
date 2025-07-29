package com.ai.test;
import com.ai.myutils.Lists;
import com.ai.myutils.Maps;
import com.ai.protocol.Awareness;
import com.ai.protocol.vo.ChangeEvent;
import com.ai.protocol.vo.UpdateEvent;
import com.ai.utils.Doc;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


public class AwarenessTest {

    /**
     * 测试Awareness功能
     */
    @Test
    public void testAwareness() {
        // 创建两个文档实例
        Doc doc1 = new Doc();
        doc1.clientID = 0;
        Doc doc2 = new Doc();
        doc2.clientID = 1;

        // 创建两个awareness实例
        Awareness aw1 = new Awareness(doc1);
        Awareness aw2 = new Awareness(doc2);

        // 用于记录最后的变化
        AtomicReference<ChangeEvent> lastChangeLocal = new AtomicReference<>();
        AtomicReference<ChangeEvent> lastChange = new AtomicReference<>();

        // 设置aw1的update监听器
        aw1.on("update", (UpdateEvent event, Object origin) -> {
//            added, updated, removed, origin
            // 当aw1更新时，将变更编码并应用到aw2
            List<Integer> allClients = new ArrayList<>();
            allClients.addAll(event.added);
            allClients.addAll(event.removed);
            allClients.addAll(event.updated);

            int[] update = Awareness.encodeAwarenessUpdate(aw1, allClients);
            Awareness.applyAwarenessUpdate(aw2, update, "custom");
        });

        // 设置aw1的change监听器
        aw1.<ChangeEvent, String>on("change", (change, origin) -> {
            lastChangeLocal.set(change);
        });

        // 设置aw2的change监听器
        aw2.<ChangeEvent, String>on("change", (change, origin) -> {
            lastChange.set(change);
        });

        // 测试1: 设置aw1的本地状态
        aw1.setLocalState(Maps.of("x", 3));

        // 验证aw2的状态
        assertEquals(Maps.of("x", 3), aw2.getStates().get(0));
        assertEquals(1, aw2.meta.get(0).clock);
        assertEquals(Lists.of(0), lastChange.get().added);
        // 创建Awareness实例时本地客户端已经标记为可用，所以不会更新
        assertEquals(new ChangeEvent(Collections.emptyList(), Lists.of(0), Collections.emptyList()),
                lastChangeLocal.get());

        // 测试2: 更新状态
        lastChange.set(null);
        lastChangeLocal.set(null);
        aw1.setLocalState(Maps.of("x", 4));

        assertEquals(Maps.of("x", 4), aw2.getStates().get(0));
        assertEquals(new ChangeEvent(Collections.emptyList(), Lists.of(0), Collections.emptyList()),
                lastChangeLocal.get());
        assertEquals(lastChangeLocal.get(), lastChange.get());

        // 测试3: 设置相同的状态
        lastChange.set(null);
        lastChangeLocal.set(null);
        aw1.setLocalState(Maps.of("x", 4));

        assertNull(lastChange.get());
        assertEquals(3, aw2.meta.get(0).clock);
        assertEquals(lastChangeLocal.get(), lastChange.get());

        // 测试4: 移除状态
        aw1.setLocalState(null);
        assertEquals(1, lastChange.get().removed.size());
        assertNull(aw1.getStates().get(0));
        assertEquals(lastChangeLocal.get(), lastChange.get());
    }
}
