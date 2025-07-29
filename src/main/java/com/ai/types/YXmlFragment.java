package com.ai.types;

import com.ai.myutils.observable.TriConsumer;
import com.ai.structs.ContentType;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;
import com.ai.utils.Doc;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * XML片段类型实现，表示一组XML节点
 */
public class YXmlFragment extends AbstractType<YXmlEvent> {

    private List<Object> _prelimContent;

    public YXmlFragment() {
        super();
        this._prelimContent = new ArrayList<>();
    }

    /**
     * 获取第一个子节点
     *
     * @return 第一个子节点，可能为YXmlElement或YXmlText
     */
    public Object getFirstChild() {
        Item first = this._first();
        return first != null ? first.content.getContent().get(0) : null;
    }

    @Override
    public void _integrate(Doc y, Item item) {
        super._integrate(y, item);
        this.insert(0, this._prelimContent);
        this._prelimContent = null;
    }

    @Override
    public YXmlFragment _copy() {
        return new YXmlFragment();
    }

    @Override
    public YXmlFragment clone() {
        YXmlFragment el = new YXmlFragment();
        el.insert(0, this.toArray().stream()
                .map(item -> item instanceof AbstractType ? ((AbstractType<?>) item).clone() : item)
                .collect(Collectors.toList()));
        return el;
    }

    public int length() {
        if (this.doc == null) warnPrematureAccess();
        return this._prelimContent != null ? this._prelimContent.size() : this._length;
    }

    /**
     * 创建子树遍历器
     *
     * @param filter 节点过滤函数
     * @return 子树遍历器
     */
    public YXmlTreeWalker createTreeWalker(Predicate<Object> filter) {
        return new YXmlTreeWalker(this, filter);
    }

    /**
     * 返回匹配查询的第一个YXmlElement，类似于DOM的querySelector
     * <p>
     * 当前支持的查询：
     * - 标签名(tagname)
     *
     * @param query 子元素查询条件
     * @return 匹配查询的第一个元素(YXmlElement | YXmlText | YXmlHook)，如果没有匹配则返回null
     */
    public Object querySelector(String query) {
        String upperQuery = query.toUpperCase();

        // 创建树遍历器
        YXmlTreeWalker iterator = new YXmlTreeWalker(this, element -> {
            if (element instanceof YXmlElement) {
                YXmlElement xmlElement = (YXmlElement) element;
                return xmlElement.nodeName != null &&
                        xmlElement.nodeName.toUpperCase().equals(upperQuery);
            }
            return false;
        });

        // 获取第一个匹配项
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * 查询匹配选择器的所有节点
     *
     * @param query CSS选择器
     * @return 匹配的节点列表
     */
    public List<Object> querySelectorAll(String query) {
        query = query.toUpperCase();
        String finalQuery = query;
        YXmlTreeWalker iterator = new YXmlTreeWalker(this,
                element -> element instanceof YXmlElement &&
                        ((YXmlElement) element).nodeName.toUpperCase().equals(finalQuery));
        List<Object> results = new ArrayList<>();
        iterator.forEachRemaining(results::add);
        return results;
    }

    @Override
    public void _callObserver(Transaction transaction, Set<String> parentSubs) {
        callTypeObservers(this, transaction, new YXmlEvent(this, parentSubs, transaction));
    }

    @Override
    public String toString() {
        return StringUtils.join(typeListMap(this, (xml, i, t) -> xml.toString()), "");
    }

    @Override
    public String toJSON() {
        return this.toString();
    }

    /**
     * 转换为DOM文档片段
     *
     * @param document DOM文档对象
     * @param hooks    钩子对象
     * @param binding  绑定对象
     * @return DOM文档片段
     */
    public Node toDOM(Document document, Map<String, Object> hooks, Object binding) {
        DocumentFragment fragment = document.createDocumentFragment();
        if (binding != null) {
            // binding._createAssociation(fragment, this);
        }
        typeListForEach(this, (xmlType, index, type) -> {
            Node node = ((YXmlFragment) xmlType).toDOM(document, hooks, binding);
            fragment.appendChild(node);
        });
        return fragment;
    }

    /**
     * 在指定位置插入内容
     *
     * @param index   插入位置
     * @param content 要插入的内容列表
     */
    public void insert(int index, List<Object> content) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeListInsertGenerics(transaction, this, index, content);
                return null;
            });
        } else {
            this._prelimContent.addAll(index, content);
        }
    }

    /**
     * 在参考节点后插入内容
     *
     * @param ref     参考节点
     * @param content 要插入的内容列表
     */
    public void insertAfter(Object ref, List<Object> content) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                Object refItem = (ref instanceof AbstractType) ? ((AbstractType<?>) ref)._item : ref;
                typeListInsertGenericsAfter(transaction, this, (Item) refItem, content);
                return refItem;
            });
        } else {
            int index = ref == null ? 0 : this._prelimContent.indexOf(ref) + 1;
            if (index == 0 && ref != null) {
                throw new RuntimeException("Reference item not found");
            }
            this._prelimContent.addAll(index, content);
        }
    }

    public void delete(int index) {
        delete(0, 1);
    }

    /**
     * 删除元素
     *
     * @param index  起始位置
     * @param length 删除数量
     */
    public void delete(int index, int length) {
        if (length == 0) return;

        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeListDelete(transaction, this, index, length);
                return null;
            });
        } else {
            this._prelimContent.subList(index, index + length).clear();
        }
    }

    /**
     * 转换为Java List
     *
     * @return 包含所有子节点的列表
     */
    public List<Object> toArray() {
        return typeListToArray(this);
    }

    /**
     * 在末尾追加内容
     *
     * @param content 要追加的内容列表
     */
    public void push(List<Object> content) {
        this.insert(this.length(), content);
    }

    /**
     * 在开头插入内容
     *
     * @param content 要插入的内容列表
     */
    public void unshift(List<Object> content) {
        this.insert(0, content);
    }

    /**
     * 获取指定位置的元素
     *
     * @param index 元素位置
     * @return 对应位置的元素
     */
    public Object get(int index) {
        return typeListGet(this, index);
    }

    /**
     * 获取子片段
     *
     * @param start 起始位置
     * @param end   结束位置
     * @return 子片段列表
     */
    public List<Object> slice(int start, int end) {
        return typeListSlice(this, start, end);
    }

    /**
     * 遍历子节点
     *
     * @param action 对每个节点执行的操作
     */
    public void forEach(TriConsumer<Object, Integer, AbstractType<?>> action) {
        typeListForEach(this, action);
    }

    @Override
    public void _write(UpdateEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlFragmentRefID);
    }

    public static AbstractType<?> readYXmlFragment(UpdateDecoder decoder) {
        return new YXmlFragment();
    }
}


