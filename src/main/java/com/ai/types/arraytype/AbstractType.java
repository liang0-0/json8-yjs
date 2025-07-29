package com.ai.types.arraytype;

import com.ai.myutils.Uint8Array;
import com.ai.myutils.observable.TriConsumer;
import com.ai.structs.ContentAny;
import com.ai.structs.ContentBinary;
import com.ai.structs.ContentDoc;
import com.ai.structs.ContentType;
import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.utils.*;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.structstore.StructStore;
import org.apache.commons.lang3.function.TriFunction;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.ai.types.arraytype.ArraySearchMarker.findMarker;

public class AbstractType<EventType> {
    // 使用特殊标记值表示undefined
    public static final Item UNDEFINED_ITEM = new Item();
    public Item _item = UNDEFINED_ITEM;
    public Map<String, Item> _map = new HashMap<>();
    public Item _start = null;
    public Doc doc = null;
    public int _length = 0;
    protected EventHandler<YEvent, Transaction> _eH = new EventHandler<>();
    public EventHandler<List<YEvent<?>>, Transaction> _dEH = new EventHandler<>();
    public List<ArraySearchMarker> _searchMarker = null;

    // 获取父类型
    public AbstractType<?> parent() {
        return _item != null ? (AbstractType<?>) _item.parent : null;
    }

    // 将此类型集成到Yjs实例中
    public void _integrate(Doc y, Item item) {
        this.doc = y;
        this._item = item;
    }

    // 复制此类型(抽象方法)
    public AbstractType<EventType> _copy() {
        return null;
    }

    // 克隆此类型(抽象方法)
    public AbstractType<EventType> clone() {
        return null;
    }

    // 写入编码器(抽象方法)
    public void _write(UpdateEncoder encoder) {

    }

    // 获取第一个未删除的项
    public Item _first() {
        Item n = _start;
        while (n != null && n.deleted()) {
            n = n.right;
        }
        return n;
    }

    // 调用观察者(抽象方法)
    public void _callObserver(Transaction transaction, Set<String> parentSubs) {
        if (!transaction.local && this._searchMarker != null) {
            this._searchMarker.clear();
        }
    }

    // 观察此类型的事件
    public void observe(BiConsumer<YEvent, Transaction> f) {
        EventHandler.addEventHandlerListener(_eH, f);
    }

    // 观察此类型及其子项的深度事件
    public void observeDeep(BiConsumer<List<YEvent<?>>, Transaction> f) {
        EventHandler.addEventHandlerListener(_dEH, f);
    }

    // 取消观察此类型的事件
    public void unobserve(BiConsumer<YEvent, Transaction> f) {
        EventHandler.removeEventHandlerListener(_eH, f);
    }

    // 取消观察此类型及其子项的深度事件
    public void unobserveDeep(BiConsumer<List<YEvent<?>>, Transaction> f) {
        EventHandler.removeEventHandlerListener(_dEH, f);
    }

    // 转换为JSON(抽象方法)
    public Object toJSON() {
        return null;
    }


    public static void warnPrematureAccess() {
        System.err.println("Invalid access: Add Yjs type to a document before reading data.");
    }

    // 获取类型的所有子项
    public static List<Item> getTypeChildren(AbstractType<?> t) {
        if (t.doc == null) {
            warnPrematureAccess();
        }
        Item s = t._start;
        List<Item> arr = new ArrayList<>();
        while (s != null) {
            arr.add(s);
            s = s.right;
        }
        return arr;
    }

    // 调用类型观察者
    public static void callTypeObservers(AbstractType<?> type,
                                         Transaction transaction,
                                         YEvent<?> event) {
        AbstractType<?> changedType = type;
        Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes = transaction.changedParentTypes;

        while (true) {
            changedParentTypes.computeIfAbsent(type, k -> new ArrayList<>()).add(event);
            if (type._item == null) {
                break;
            }
            type = (AbstractType<?>) type._item.parent;
        }
        EventHandler.callEventHandlerListeners(changedType._eH, event, transaction);
    }

    // 类型列表切片
    public static <T> List<T> typeListSlice(AbstractType<?> type, int start, int end) {
        if (type.doc == null) {
            warnPrematureAccess();
        }
        if (start < 0) {
            start = type._length + start;
        }
        if (end < 0) {
            end = type._length + end;
        }
        int len = end - start;
        List<T> cs = new ArrayList<>();
        Item n = type._start;

        while (n != null && len > 0) {
            if (n.countable() && !n.deleted()) {
                List<T> c = (List<T>) n.content.getContent();
                if (c.size() <= start) {
                    start -= c.size();
                } else {
                    for (int i = start; i < c.size() && len > 0; i++) {
                        cs.add(c.get(i));
                        len--;
                    }
                    start = 0;
                }
            }
            n = n.right;
        }
        return cs;
    }

    // 类型列表转数组
    public static <T> List<T> typeListToArray(AbstractType<?> type) {
        if (type.doc == null) {
            warnPrematureAccess();
        }
        List<T> cs = new ArrayList<>();
        Item n = type._start;

        while (n != null) {
            if (n.countable() && !n.deleted()) {
                List<T> c = (List<T>) n.content.getContent();
                cs.addAll(c);
            }
            n = n.right;
        }
        return cs;
    }

    // 快照下的类型列表转数组
    public static List<Object> typeListToArraySnapshot(AbstractType<?> type, Snapshot snapshot) {
        List<Object> cs = new ArrayList<>();
        Item n = type._start;

        while (n != null) {
            if (n.countable() && Snapshot.isVisible(n, snapshot)) {
                List<Object> c = n.content.getContent();
                cs.addAll(c);
            }
            n = n.right;
        }
        return cs;
    }

    // 遍历类型列表
    public static void typeListForEach(AbstractType<?> type, TriConsumer<Object, Integer, AbstractType<?>> f) {
        int index = 0;
        Item n = type._start;
        if (type.doc == null) {
            warnPrematureAccess();
        }

        while (n != null) {
            if (n.countable() && !n.deleted()) {
                List<Object> c = n.content.getContent();
                for (Object o : c) {
                    f.accept(o, index++, type);
                }
            }
            n = n.right;
        }
    }

    public static Integer counter = 1;

    // 映射类型列表
    public static <C, R> List<R> typeListMap(AbstractType<?> type, TriFunction<C, Integer, AbstractType<?>, R> f) {
        List<R> result = new ArrayList<>();
        typeListForEach(type, (c, i, t) -> {
            result.add(f.apply((C) c, i, t));
        });
        return result;
    }

    // 创建类型列表迭代器
    public static <T> Iterator<T> typeListCreateIterator(AbstractType<?> type) {
        return new Iterator<T>() {
            Item n = type._start;
            List<T> currentContent = null;
            int currentContentIndex = 0;

            @Override
            public boolean hasNext() {
                if (currentContent == null) {
                    while (n != null && n.deleted()) {
                        n = n.right;
                    }
                    if (n == null) {
                        return false;
                    }
                    currentContent = (List<T>) n.content.getContent();
                    currentContentIndex = 0;
                    n = n.right;
                }
                return currentContentIndex < currentContent.size();
            }

            @Override
            public T next() {
                T value = (T) currentContent.get(currentContentIndex++);
                if (currentContentIndex >= currentContent.size()) {
                    currentContent = null;
                }
                return value;
            }
        };
    }

    // 快照下的遍历类型列表
    public static void typeListForEachSnapshot(AbstractType<?> type,
                                               TriConsumer<Object, Integer, AbstractType<?>> f,
                                               Snapshot snapshot) {
        int index = 0;
        Item n = type._start;

        while (n != null) {
            if (n.countable() && Snapshot.isVisible(n, snapshot)) {
                List<Object> c = n.content.getContent();
                for (int i = 0; i < c.size(); i++) {
                    f.accept(c.get(i), index++, type);
                }
            }
            n = n.right;
        }
    }

    // 获取类型列表元素
    public static Object typeListGet(AbstractType<?> type, int index) {
        if (type.doc == null) {
            warnPrematureAccess();
        }
        ArraySearchMarker marker = findMarker(type, index);
        Item n = type._start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
        }

        for (; n != null; n = n.right) {
            if (!n.deleted() && n.countable()) {
                if (index < n.length) {
                    return n.content.getContent().get(index);
                }
                index -= n.length;
            }
        }
        return null;
    }

    // 在类型列表后插入通用内容
    public static void typeListInsertGenericsAfter(Transaction transaction,
                                                   AbstractType<?> parent,
                                                   Item referenceItem,
                                                   List<?> content) {
        AtomicReference<Item> left = new AtomicReference<>(referenceItem);
        Doc doc = transaction.doc;
        int ownClientId = doc.clientID;
        StructStore store = doc.store;
        Item right = referenceItem == null ? parent._start : referenceItem.right;
        AtomicReference<List<Object>> jsonContent = new AtomicReference<>(new ArrayList<>());

        Consumer<Void> packJsonContent = (p) -> {
            if (!jsonContent.get().isEmpty()) {
                left.set(new Item(
                        ID.createID(ownClientId, StructStore.getState(store, ownClientId)),
                        left.get(),
                        left.get() != null ? left.get().getLastId() : null,
                        right,
                        right != null ? right.id : null,
                        parent,
                        null,
                        new ContentAny(jsonContent.get())
                ));
                left.get().integrate(transaction, 0);
                jsonContent.set(new ArrayList<>());
            }
        };

        for (Object c : content) {
            if (c == null) {
                jsonContent.get().add(c);
            } else {
                if (c instanceof Number || c instanceof Boolean || c instanceof Map || c instanceof List || c instanceof String || c instanceof Date) {
                    jsonContent.get().add(c);
                } else {
                    packJsonContent.accept(null);
                    if (c instanceof int[] || c instanceof ByteBuffer) {
                        left.set(new Item(
                                ID.createID(ownClientId, StructStore.getState(store, ownClientId)),
                                left.get(),
                                left.get() != null ? left.get().getLastId() : null,
                                right,
                                right != null ? right.id : null,
                                parent,
                                null,
                                new ContentBinary(c instanceof int[] ? (int[]) c : Uint8Array.toIntArray(((ByteBuffer) c).array()))
                        ));
                        left.get().integrate(transaction, 0);
                    } else if (c instanceof Doc) {
                        left.set(new Item(
                                ID.createID(ownClientId, StructStore.getState(store, ownClientId)),
                                left.get(),
                                left.get() != null ? left.get().getLastId() : null,
                                right,
                                right != null ? right.id : null,
                                parent,
                                null,
                                new ContentDoc((Doc) c)
                        ));
                        left.get().integrate(transaction, 0);
                    } else if (c instanceof AbstractType) {
                        left.set(new Item(
                                ID.createID(ownClientId, StructStore.getState(store, ownClientId)),
                                left.get(),
                                left.get() != null ? left.get().getLastId() : null,
                                right,
                                right != null ? right.id : null,
                                parent,
                                null,
                                new ContentType((AbstractType<?>) c)
                        ));
                        left.get().integrate(transaction, 0);
                    } else {
                        throw new RuntimeException("Unexpected content type in insert operation");
                    }
                }
            }
        }
        packJsonContent.accept(null);
    }

    // 在类型列表指定位置插入通用内容
    public static void typeListInsertGenerics(Transaction transaction,
                                              AbstractType<?> parent,
                                              int index,
                                              List content) {
        if (index > parent._length) {
            throw new RuntimeException("Length exceeded!");
        }
        if (index == 0) {
            if (parent._searchMarker != null) {
                ArraySearchMarker.updateMarkerChanges(parent._searchMarker, index, content.size());
            }
            typeListInsertGenericsAfter(transaction, parent, null, content);
            return;
        }

        int startIndex = index;
        ArraySearchMarker marker = findMarker(parent, index);
        Item n = parent._start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
            if (index == 0) {
                n = n.prev();
                index += (n != null && n.countable() && !n.deleted()) ? n.length : 0;
            }
        }

        for (; n != null; n = n.right) {
            if (!n.deleted() && n.countable()) {
                if (index <= n.length) {
                    if (index < n.length) {
                        StructStore.getItemCleanStart(transaction, ID.createID(n.id.client, n.id.clock + index));
                    }
                    break;
                }
                index -= n.length;
            }
        }

        if (parent._searchMarker != null) {
            ArraySearchMarker.updateMarkerChanges(parent._searchMarker, startIndex, content.size());
        }
        typeListInsertGenericsAfter(transaction, parent, n, content);
    }

    /**
     * 推送内容到列表末尾，避免更新搜索标记
     *
     * @param transaction 事务对象
     * @param parent      父类型
     * @param content     要插入的内容列表
     */
    public static void typeListPushGenerics(Transaction transaction,
                                            AbstractType<?> parent,
                                            List<?> content) {
        // 获取所有搜索标记，如果没有则为空列表
        List<ArraySearchMarker> searchMarkers = Optional.ofNullable(parent._searchMarker)
                .orElse(Collections.emptyList());

        // 找到索引最大的标记，如果没有则使用默认值(index=0, p=parent._start)
        ArraySearchMarker marker = searchMarkers.stream()
                .reduce(new ArraySearchMarker(0, parent._start),
                        (currentMax, maxMarker) ->
                                maxMarker.index > currentMax.index ? maxMarker : currentMax);

        // 从标记指向的节点开始，找到最右边的节点
        Item n = marker.p;
        if (n != null) {
            while (n.right != null) {
                n = n.right;
            }
        }

        // 在最后一个节点后插入内容
        typeListInsertGenericsAfter(transaction, parent, n, content);
    }

    // 删除类型列表元素
    public static void typeListDelete(Transaction transaction, AbstractType<?> parent, int index, int length) {
        if (length == 0) return;

        int startIndex = index;
        int startLength = length;
        ArraySearchMarker marker = findMarker(parent, index);
        Item n = parent._start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
        }

        for (; n != null && index > 0; n = n.right) {
            if (!n.deleted() && n.countable()) {
                if (index < n.length) {
                    StructStore.getItemCleanStart(transaction, ID.createID(n.id.client, n.id.clock + index));
                }
                index -= n.length;
            }
        }

        while (length > 0 && n != null) {
            if (!n.deleted()/* && n.countable()*/) {
                if (length < n.length) {
                    StructStore.getItemCleanStart(transaction, ID.createID(n.id.client, n.id.clock + length));
                }
                n.delete(transaction);
                length -= n.length;
            }
            n = n.right;
        }

        if (length > 0) {
            throw new RuntimeException("Length exceeded!");
        }

        if (parent._searchMarker != null) {
            ArraySearchMarker.updateMarkerChanges(parent._searchMarker, startIndex, -startLength + length);
        }
    }

    // 删除类型映射中的键
    public static void typeMapDelete(Transaction transaction, AbstractType<?> parent, String key) {
        Item c = parent._map.get(key);
        if (c != null) {
            c.delete(transaction);
        }
    }

    // 设置类型映射键值
    public static void typeMapSet(Transaction transaction, AbstractType<?> parent, String key, Object value) {
        Item left = parent._map.getOrDefault(key, null);
        Doc doc = transaction.doc;
        int ownClientId = doc.clientID;
        AbstractContent content;

        if (value == null) {
            content = new ContentAny(Collections.singletonList(value));
        } else {
            if (value instanceof Number || value instanceof Boolean ||
                    value instanceof Map || value instanceof List ||
                    value instanceof String || value instanceof Date ||
                    value instanceof Long || value instanceof Double) {
                content = new ContentAny(Collections.singletonList(value));
            } else if (value instanceof int[] || value instanceof ByteBuffer) {
                content = new ContentBinary(value instanceof int[] ? (int[]) value : Uint8Array.toIntArray(((ByteBuffer) value).array()));
            } else if (value instanceof Doc) {
                content = new ContentDoc((Doc) value);
            } else if (value instanceof AbstractType) {
                content = new ContentType((AbstractType<?>) value);
            } else {
                throw new RuntimeException("Unexpected content type");
            }
        }

        new Item(ID.createID(ownClientId, StructStore.getState(doc.store, ownClientId)),
                left,
                left != null ? left.getLastId() : null,
                null,
                null,
                parent,
                key,
                content
        ).integrate(transaction, 0);
    }

    // 获取类型映射值
    public static Object typeMapGet(AbstractType<?> parent, String key) {
        if (parent.doc == null) {
            warnPrematureAccess();
        }
        Item val = parent._map.get(key);
        return val != null && !val.deleted() ? val.content.getContent().get(val.length - 1) : null;
    }

    // 获取类型映射所有键值
    public static Map<String, Object> typeMapGetAll(AbstractType<?> parent) {
        Map<String, Object> res = new HashMap<>();
        if (parent.doc == null) {
            warnPrematureAccess();
        }
        parent._map.forEach((key, value) -> {
            if (!value.deleted()) {
                res.put(key, value.content.getContent().get(value.length - 1));
            }
        });
        return res;
    }

    // 检查类型映射是否包含键
    public static boolean typeMapHas(AbstractType<?> parent, String key) {
        if (parent.doc == null) {
            warnPrematureAccess();
        }
        Item val = parent._map.get(key);
        return val != null && !val.deleted();
    }

    // 快照下获取类型映射值
    public static Object typeMapGetSnapshot(AbstractType<?> parent, String key, Snapshot snapshot) {
        Item v = parent._map.getOrDefault(key, null);
        while (v != null && (!snapshot.sv.containsKey(v.id.client) || v.id.clock >= snapshot.sv.getOrDefault(v.id.client, 0))) {
            v = v.left;
        }
        return v != null && Snapshot.isVisible(v, snapshot) ? v.content.getContent().get(v.length - 1) : null;
    }

    // 快照下获取类型映射所有键值
    public static Map<String, Object> typeMapGetAllSnapshot(AbstractType<?> parent, Snapshot snapshot) {
        Map<String, Object> res = new HashMap<>();
        parent._map.forEach((key, value) -> {
            Item v = value;
            while (v != null && (!snapshot.sv.containsKey(v.id.client) || v.id.clock >= snapshot.sv.getOrDefault(v.id.client, 0))) {
                v = v.left;
            }
            if (v != null && Snapshot.isVisible(v, snapshot)) {
                res.put(key, v.content.getContent().get(v.length - 1));
            }
        });
        return res;
    }

    // 创建类型映射迭代器
    public static Iterator<Map.Entry<String, Item>> createMapIterator(AbstractType<?> type) {
        if (type.doc == null) {
            warnPrematureAccess();
        }
        return type._map.entrySet().stream()
                .filter(entry -> !entry.getValue().deleted())
                .collect(Collectors.toList()).iterator();
    }

}
