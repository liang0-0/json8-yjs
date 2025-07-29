package com.ai.test.helper;

import com.ai.Y;
import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.YXmlElement;
import com.ai.types.arraytype.AbstractType;
import com.ai.utils.Doc;
import com.ai.utils.structstore.StructStore;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Yjs测试辅助类，提供多客户端同步测试的支持
 */
public class TestHelper {
    // 编码版本控制
    private static boolean useV2 = false;

    // V1编码器
    private static final Encoder encV1 = new Encoder(
            Y::encodeStateAsUpdate,
            Y::mergeUpdates,
            Y::applyUpdate,
            Y::logUpdate,
            "update",
            Y::diffUpdate
    );

    // V2编码器
    private static final Encoder encV2 = new Encoder(
            Y::encodeStateAsUpdateV2,
            Y::mergeUpdatesV2,
            Y::applyUpdateV2,
            Y::logUpdateV2,
            "updateV2",
            Y::diffUpdateV2
    );

    // 当前使用的编码器
    public static Encoder enc = encV1;

    /**
     * 使用V1编码
     */
    public static void useV1Encoding() {
        useV2 = false;
        enc = encV1;
    }

    /**
     * 使用V2编码
     */
    public static void useV2Encoding() {
        System.err.println("同步协议尚不支持V2协议，回退到V1编码");
        useV2 = false;
        enc = encV1;
    }


    /**
     * 初始化测试环境
     *
     * @param tc             测试用例
     * @param users          用户数量
     * @param initTestObject 初始化测试对象的函数
     * @return 包含测试环境的Map
     */
    public static Map<String, Object> init(TestCase tc, int users, Function<TestYInstance, Object> initTestObject) {
        Map<String, Object> result = new HashMap<>();
        Random gen = tc.prng;

        // 随机选择编码方式
        if (gen.nextBoolean()) {
            useV2Encoding();
        } else {
            useV1Encoding();
        }

        TestConnector testConnector = new TestConnector(gen);
        result.put("testConnector", testConnector);

        List<TestYInstance> userList = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            TestYInstance y = testConnector.createY(i);
            y.clientID = i;
            userList.add(y);

            // 添加各种类型的数据结构
            result.put("array" + i, y.getArray("array"));
            result.put("map" + i, y.getMap("map"));
            result.put("xml" + i, y.get("xml", YXmlElement::new));
            result.put("text" + i, y.getText("text"));
        }
        result.put("users", userList);

        testConnector.syncAll();

        // 初始化测试对象
        List<Object> testObjects = new ArrayList<>();
        for (TestYInstance user : userList) {
            testObjects.add(initTestObject != null ? initTestObject.apply(user) : null);
        }
        result.put("testObjects", testObjects);

        useV1Encoding();
        return result;
    }

    /**
     * 比较多个用户的状态是否一致
     *
     * @param users 用户列表
     */
    public static void compare(List<Doc> users) {
        // 重新连接并同步所有消息
        for (Doc user : users) {
            ((TestYInstance) user).connect();
        }

        // 确保所有消息都已处理
        while (((TestYInstance) users.get(0)).tc.flushAllMessages()) {
        }

        // 创建合并后的文档用于验证
        List<Doc> mergedDocs = new ArrayList<>();
        for (Doc user : users) {
            Doc ydoc = new Doc();
            enc.applyUpdate.accept(ydoc, enc.mergeUpdates.apply(((TestYInstance) user).updates));
            mergedDocs.add(ydoc);
        }
        users.addAll(mergedDocs);

        // 收集各用户的数据状态
        List<Object> userArrayValues = new ArrayList<>();
        List<Map<String, Object>> userMapValues = new ArrayList<>();
        List<String> userXmlValues = new ArrayList<>();
        List<List<JSONObject>> userTextValues = new ArrayList<>();

        for (Doc user : users) {
            userArrayValues.add(user.getArray("array").toJSON());
            userMapValues.add(user.getMap("map").toJSON());
            userXmlValues.add(user.get("xml", YXmlElement::new).toString());
            userTextValues.add(user.getText("text").toDelta());

            // 验证数据结构
            assert user.store.pendingDs == null;
            assert user.store.pendingStructs == null;
        }

        // 验证迭代器
        List<Object> arrayFromIterator = new ArrayList<>();
        for (Object item : users.get(0).getArray("array")) {
            arrayFromIterator.add(item);
        }
        assertEquals(users.get(0).getArray("array").toArray(), arrayFromIterator);

        // 验证Map迭代器
        Iterable<String> ymapkeys = users.get(0).getMap("map").keys();
        assertEquals(CollectionUtils.size(ymapkeys), userMapValues.get(0).size());

        Map<String, Object> mapRes = new HashMap<>();
        for (Map.Entry<String, Item> entry : users.get(0).getMap("map").entries()) {
            Object value = entry.getValue();
            mapRes.put(entry.getKey(), value);
        }
        assertEquals(userMapValues.get(0), mapRes);

        // 比较所有用户状态
        for (int i = 0; i < users.size() - 1; i++) {
            assertEquals(userArrayValues.get(i), userArrayValues.get(i + 1));
            assertEquals(userMapValues.get(i), userMapValues.get(i + 1));
            assertEquals(userXmlValues.get(i), userXmlValues.get(i + 1));

            // 比较文本长度
            int textLength1 = calculateTextLength(userTextValues.get(i));
            int textLength2 = users.get(i).getText("text").length();
            assertEquals(textLength1, textLength2);

            assertEquals(userTextValues.get(i), userTextValues.get(i + 1));
            assertArrayEquals(Y.encodeStateVector(users.get(i)),
                    Y.encodeStateVector(users.get(i + 1)));

            Y.equalDeleteSets(
                    Y.createDeleteSetFromStructStore(users.get(i).store),
                    Y.createDeleteSetFromStructStore(users.get(i + 1).store));

            compareStructStores(users.get(i).store, users.get(i + 1).store);

            assertArrayEquals(
                    Y.encodeSnapshot(Y.snapshot(users.get(i))),
                    Y.encodeSnapshot(Y.snapshot(users.get(i + 1))));
        }

        // 清理
        for (Doc user : users) {
            user.destroy();
        }
    }

    /**
     * 比较两个结构存储是否相同
     *
     * @param ss1 结构存储1
     * @param ss2 结构存储2
     */
    private static void compareStructStores(StructStore ss1, StructStore ss2) {
        assertEquals(ss1.clients.size(), ss2.clients.size());

        for (Map.Entry<Integer, List<AbstractStruct>> entry1 : ss1.clients.entrySet()) {
            List<AbstractStruct> structs2 = ss2.clients.get(entry1.getKey());
            assertNotNull(structs2);
            assertEquals(entry1.getValue().size(), structs2.size());

            for (int i = 0; i < entry1.getValue().size(); i++) {
                AbstractStruct s1 = entry1.getValue().get(i);
                AbstractStruct s2 = structs2.get(i);

                // 检查基本属性
                assertEquals(s1.getClass(), s2.getClass());
                assertTrue(Y.compareIDs(s1.id, s2.id));
                assertEquals(s1.deleted(), s2.deleted());
                assertEquals(s1.length, s2.length);

                if (s1 instanceof Item) {
                    Item item1 = (Item) s1;
                    assertTrue(s2 instanceof Item);
                    Item item2 = (Item) s2;

                    // 检查连接关系
                    if (item1.left == null) {
                        assertNull(item2.left);
                    } else {
                        assertNotNull(item2.left);
                        assertTrue(Y.compareIDs(item1.left.getLastId(), item2.left.getLastId()));
                    }

                    assertTrue(compareItemIDs(item1.right, item2.right));
                    assertTrue(Y.compareIDs(item1.origin, item2.origin));
                    assertTrue(Y.compareIDs(item1.rightOrigin, item2.rightOrigin));
                    assertEquals(item1.parentSub, item2.parentSub);

                    // 验证连接正确性
                    assertTrue(item1.left == null || item1.left.right == item1);
                    assertTrue(item1.right == null || item1.right.left == item1);
                    assertTrue(item2.left == null || item2.left.right == item2);
                    assertTrue(item2.right == null || item2.right.left == item2);
                }
            }
        }
    }

    /**
     * 比较两个Item ID是否相同
     */
    private static boolean compareItemIDs(Item a, Item b) {
        return a == b || (a != null && b != null && Y.compareIDs(a.id, b.id));
    }

    /**
     * 计算文本长度
     */
    private static int calculateTextLength(List<JSONObject> deltas) {
        int length = 0;
        for (JSONObject delta : deltas) {
            if (delta instanceof JSONObject) {
                JSONObject map = delta;
                if (map.containsKey("insert") && map.get("insert") instanceof String) {
                    length += ((String) map.get("insert")).length();
                }
            }
        }
        return length;
    }

    /**
     * 广播消息给其他客户端
     */
    public static void broadcastMessage(TestYInstance sender, int[] message) {
        if (sender.tc.onlineConns.contains(sender)) {
            for (TestYInstance remote : sender.tc.onlineConns) {
                if (remote != sender) {
                    remote._receive(message, sender);
                }
            }
        }
    }

    // 辅助方法
    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("Byte arrays not equal");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition not true");
        }
    }

    private static void assertNotNull(Object obj) {
        if (obj == null) {
            throw new AssertionError("Object is null");
        }
    }


    /**
     * 测试用例类
     */
    public static class TestCase {
        public final Random prng;

        public TestCase(Random prng) {
            this.prng = prng;
        }
    }


    @FunctionalInterface
    public interface BiConsumer<T, U> {
        void accept(T t, U u);
    }

    @FunctionalInterface
    public interface BiFunction<T, U, R> {
        R apply(T t, U u);
    }
}