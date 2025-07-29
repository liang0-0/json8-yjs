package com.ai.types;

import com.ai.structs.ContentType;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.vo.YMapEvent;
import com.ai.utils.Doc;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;

/**
 * 共享Map实现，对应Yjs中的YMap
 */
public class YMap extends AbstractType<YMapEvent> implements Iterable<Map.Entry<String, Item>> {

    // 临时存储初始化内容
    private Map<String, Object> _prelimContent;

    /**
     * 构造空YMap
     */
    public YMap() {
        this(null);
    }

    /**
     * 从现有条目构造YMap
     * @param entries 初始条目集合
     */
    public YMap(Iterable<Map.Entry<String, Object>> entries) {
        super();
        this._prelimContent = new HashMap<>();
        if (entries != null) {
            entries.forEach(entry -> this._prelimContent.put(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * 集成到Yjs文档
     * @param y 文档实例
     * @param item 关联的项
     */
    @Override
    public void _integrate(Doc y, Item item) {
        super._integrate(y, item);
        this._prelimContent.forEach(this::set);
        this._prelimContent = null;
    }

    /**
     * 创建副本
     * @return 新YMap实例
     */
    @Override
    public AbstractType _copy() {
        return new YMap();
    }

    /**
     * 克隆Map
     * @return 克隆后的新Map
     */
    @Override
    public YMap clone() {
        YMap map = new YMap();
        this.forEach((value, key) -> {
            Object clonedValue = value instanceof AbstractType ? 
                ((AbstractType<?>)value).clone() : value;
            map.set(key, (Object)clonedValue);
        });
        return map;
    }

    /**
     * 调用观察者
     * @param transaction 当前事务
     * @param parentSubs 父级变更集合
     */
    @Override
    public void _callObserver(Transaction transaction, Set<String> parentSubs) {
        callTypeObservers(this, transaction, new YMapEvent<>(this, transaction, parentSubs));
    }

    /**
     * 转换为JSON格式
     * @return JSON格式的Map
     */
    @Override
    public Map<String, Object> toJSON() {
        if (this.doc == null) warnPrematureAccess();
        Map<String, Object> jsonMap = new HashMap<>();
        this._map.forEach((key, item) -> {
            if (!item.deleted()) {
                Object value = item.content.getContent().get(item.length - 1);
                jsonMap.put(key, value instanceof AbstractType ? 
                    ((AbstractType<?>)value).toJSON() : value);
            }
        });
        return jsonMap;
    }

    /**
     * 获取Map大小
     * @return 键值对数量
     */
    public int size() {
        return (int)StreamSupport.stream(this.spliterator(), false).count();
    }

    /**
     * 获取键集合
     * @return 键的迭代器
     */
    public Iterable<String> keys() {
        return () -> new Iterator<String>() {
            private final Iterator<Map.Entry<String, Item>> iterator = iterator();
            
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public String next() {
                return iterator.next().getKey();
            }
        };
    }

    /**
     * 获取值集合
     * @return 值的迭代器
     */
    public Iterable<Object> values() {
        return () -> new Iterator<Object>() {
            private final Iterator<Map.Entry<String, Item>> iterator = iterator();
            
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return iterator.next().getValue();
            }
        };
    }

    /**
     * 获取条目集合
     *
     * @return 条目的迭代器
     */
    public YMap entries() {
        return this;
    }

    /**
     * 遍历Map元素
     * @param action 对每个元素执行的操作
     */
    public void forEach(BiConsumer<Object, String> action) {
        if (this.doc == null) warnPrematureAccess();
        this._map.forEach((key, item) -> {
            if (!item.deleted()) {
                action.accept((Object) item.content.getContent().get(item.length - 1), key);
            }
        });
    }

    /**
     * 获取迭代器
     * @return Map条目迭代器
     */
    @Override
    public Iterator<Map.Entry<String, Item>> iterator() {
        return createMapIterator(this);
    }

    /**
     * 删除指定键的元素
     * @param key 要删除的键
     */
    public void delete(String key) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeMapDelete(transaction, this, key);
                return null;
            });
        } else {
            this._prelimContent.remove(key);
        }
    }

    /**
     * 设置键值对
     * @param key 键
     * @param value 值
     * @return 设置的值
     */
    public Object set(String key, Object value) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeMapSet(transaction, this, key, value);
                return null;
            });
        } else {
            this._prelimContent.put(key, value);
        }
        return value;
    }

    /**
     * 获取指定键的值
     * @param key 键
     * @return 对应的值，不存在则返回null
     */
    public Object get(String key) {
        return typeMapGet(this, key);
    }

    /**
     * 检查是否包含指定键
     * @param key 键
     * @return 是否包含
     */
    public boolean has(String key) {
        return typeMapHas(this, key);
    }

    /**
     * 清空Map
     */
    public void clear() {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                this.forEach((value, key) -> {
                    typeMapDelete(transaction, this, key);
                });
                return null;
            });
        } else {
            this._prelimContent.clear();
        }
    }

    /**
     * 写入编码器
     * @param encoder 编码器实例
     */
    @Override
    public void _write(UpdateEncoder encoder) {
        encoder.writeTypeRef(ContentType.YMapRefID);
    }

    public static AbstractType<?> readYMap(UpdateDecoder updateDecoder) {
        return new YMap();
    }
}

