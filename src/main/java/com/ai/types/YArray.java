package com.ai.types;

import com.ai.myutils.observable.TriConsumer;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.arraytype.ArraySearchMarker;
import com.ai.types.vo.YArrayEvent;
import com.ai.utils.Doc;
import com.ai.utils.Transaction;
import com.ai.utils.YEvent;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ai.structs.ContentType.YArrayRefID;
import static com.ai.utils.Transaction.transact;

/**
 * 共享数组实现，对应 Yjs 中的 YArray
 * @param <T> 数组元素类型
 */
public class YArray<T> extends AbstractType<YEvent> implements Iterable<T> {
    // 临时存储初始化内容
    private List<T> _prelimContent = new ArrayList<>();
    // 搜索标记列表
    private List<ArraySearchMarker> _searchMarker = new ArrayList<>();

    /**
     * 构造空数组
     */
    public YArray() {
        super();
    }

    /**
     * 从给定项构造新YArray
     * @param <T> 元素类型
     * @param items 初始项集合
     * @return 新建的YArray
     */
    public static <T> YArray<T> from(List<T> items) {
        YArray<T> array = new YArray<>();
        array.push(items);
        return array;
    }

    /**
     * 集成到Yjs文档
     * @param y 文档实例
     * @param item 关联的项
     */
    @Override
    public void _integrate(Doc y, Item item) {
        super._integrate(y, item);
        this.insert(0, this._prelimContent);
        this._prelimContent = null;
    }

    /**
     * 创建副本
     * @return 新YArray实例
     */
    @Override
    public YArray<T> _copy() {
        return new YArray<>();
    }

    /**
     * 克隆数组
     * @return 克隆后的新数组
     */
    @Override
    public YArray<T> clone() {
        YArray<T> arr = new YArray<>();
        arr.insert(0, (List<T>) this.toArray().stream().map(el -> el instanceof AbstractType ? ((AbstractType<T>) el).clone() : el).collect(Collectors.toList()));
        return arr;
    }

    /**
     * 获取数组长度
     * @return 数组长度
     */
    public int length() {
        if (this.doc == null) warnPrematureAccess();
        return this._length;
    }

    /**
     * 调用观察者
     * @param transaction 当前事务
     * @param parentSubs 父级变更集合
     */
    @Override
    public void _callObserver(Transaction transaction, Set<String> parentSubs) {
        super._callObserver(transaction, parentSubs);
        callTypeObservers(this, transaction, new YArrayEvent<>(this, transaction));
    }

    /**
     * 在指定位置插入元素
     * @param index 插入位置
     * @param content 要插入的内容列表
     */
    public void insert(int index, List<T> content) {
        if (this.doc != null) {
            transact(this.doc, transaction -> {
                typeListInsertGenerics(transaction, this, index, content);
                return null;
            });
        } else {
            this._prelimContent.addAll(index, content);
        }
    }

    /**
     * 追加元素到数组末尾
     * @param content 要追加的内容列表
     */
    public void push(List<T> content) {
        if (this.doc != null) {
            transact(this.doc, transaction -> {
                typeListPushGenerics(transaction, this, content);
                return null;
            });
        } else {
            this._prelimContent.addAll(content);
        }
    }

    /**
     * 在数组开头添加元素
     * @param content 要添加的内容列表
     */
    public void unshift(List<T> content) {
        this.insert(0, content);
    }

    public void delete(int index) {
        delete(index, 1);
    }

    /**
     * 删除元素
     * @param index 起始位置
     * @param length 删除长度(默认为1)
     */
    public void delete(int index, int length) {
        if (length == 0) return;
        
        if (this.doc != null) {
            transact(this.doc, transaction -> {
                typeListDelete(transaction, this, index, length);
                return null;
            });
        } else {
            if (index < 0 || index >= this._prelimContent.size()) {
                throw new IndexOutOfBoundsException();
            }
            int end = Math.min(index + length, this._prelimContent.size());
            this._prelimContent.subList(index, end).clear();
        }
    }

    /**
     * 获取指定位置元素
     * @param index 元素位置
     * @return 对应位置的元素
     */
    public T get(int index) {
        return (T) typeListGet(this, index);
    }

    /**
     * 转换为Java List
     * @return 包含所有元素的List
     */
    public List<T> toArray() {
        return typeListToArray(this);
    }

    /**
     * 获取子数组
     * @param start 起始位置(包含)
     * @param end 结束位置(不包含)
     * @return 子数组
     */
    public List<T> slice(int start, int end) {
        return typeListSlice(this, start, end);
    }

    /**
     * 转换为JSON格式
     * @return JSON格式的List
     */
    @Override
    public List<Object> toJSON() {
        return this.map(c -> c instanceof AbstractType ? ((AbstractType<?>)c).toJSON() : c);
    }

    /**
     * 映射数组元素
     * @param <M> 结果类型
     * @param f 映射函数
     * @return 映射后的新List
     */
    public <M> List<M> map(Function<T, M> f) {
        return typeListMap(this, (item, index, parent) -> f.apply((T)item));
    }

    /**
     * 遍历数组元素
     * @param f 对每个元素执行的操作
     */

    public void forEach(TriConsumer<Object, Integer, AbstractType<?>> f) {
        typeListForEach(this, f);
    }

    /**
     * 获取迭代器
     * @return 数组迭代器
     */
    @Override
    public Iterator iterator() {
        return typeListCreateIterator(this);
    }

    /**
     * 写入编码器
     * @param encoder 编码器实例
     */
    @Override
    public void _write(UpdateEncoder encoder) {
        encoder.writeTypeRef(YArrayRefID);
    }

    //export const readYArray = _decoder => new YArray()
    public static AbstractType<?> readYArray(UpdateDecoder _decoder) {
        return new YArray<>();
    }
}
