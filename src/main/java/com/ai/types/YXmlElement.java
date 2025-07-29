package com.ai.types;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import com.ai.structs.ContentType;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;
import com.ai.utils.Doc;
import com.ai.utils.Snapshot;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * XML元素类型实现，模拟DOM元素行为
 *
 * @param <KV> 属性键值对类型
 */
public class YXmlElement<KV extends Map<String, Object>> extends YXmlFragment {

    public String nodeName;
    private Map<String, Object> _prelimAttrs;

    /**
     * 构造XML元素
     *
     * @param nodeName 节点名称，默认为"UNDEFINED"
     */
    public YXmlElement(String nodeName) {
        super();
        this.nodeName = nodeName != null ? nodeName : "UNDEFINED";
        this._prelimAttrs = new HashMap<>();
    }

    public YXmlElement() {
        this("UNDEFINED");
    }

    /**
     * 获取下一个兄弟节点
     *
     * @return 下一个兄弟节点，可能为YXmlElement或YXmlText
     */
    public Object getNextSibling() {
        Item n = this._item != null ? this._item.next() : null;
        return n != null ? ((ContentType) n.content).type : null;
    }

    /**
     * 获取上一个兄弟节点
     *
     * @return 上一个兄弟节点，可能为YXmlElement或YXmlText
     */
    public Object getPrevSibling() {
        Item n = this._item != null ? this._item.prev() : null;
        return n != null ? ((ContentType) n.content).type : null;
    }

    @Override
    public void _integrate(Doc y, Item item) {
        super._integrate(y, item);
        this._prelimAttrs.forEach(this::setAttribute);
        this._prelimAttrs = null;
    }

    @Override
    public YXmlElement<KV> _copy() {
        return new YXmlElement<>(this.nodeName);
    }

    @Override
    public YXmlElement<KV> clone() {
        YXmlElement<KV> el = new YXmlElement<>(this.nodeName);
        Map<String, Object> attrs = this.getAttributes();
        attrs.forEach((key, value) -> {
            if (value instanceof String) {
                el.setAttribute(key, (String) value);
            }
        });
        el.insert(0, this.toArray().stream()
                .map(item -> item instanceof AbstractType ? ((AbstractType<?>) item).clone() : item)
                .collect(Collectors.toList()));
        return el;
    }

    /**
     * 获取XML序列化字符串
     *
     * @return XML格式字符串
     */
    @Override
    public String toString() {
        Map<String, Object> attrs = this.getAttributes();
        StringBuilder stringBuilder = new StringBuilder();

        // 按属性名排序
        List<String> keys = new ArrayList<>(attrs.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            stringBuilder.append(" ").append(key).append("=\"").append(attrs.get(key)).append("\"");
        }

        String nodeName = this.nodeName.toLowerCase();
        return "<" + nodeName + stringBuilder.toString() + ">" +
                super.toString() + "</" + nodeName + ">";
    }

    /**
     * 删除属性
     *
     * @param attributeName 属性名
     */
    public void removeAttribute(String attributeName) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeMapDelete(transaction, this, attributeName);
                return null;
            });
        } else {
            this._prelimAttrs.remove(attributeName);
        }
    }

    /**
     * 设置或更新属性
     *
     * @param attributeName  属性名
     * @param attributeValue 属性值
     */
    public void setAttribute(String attributeName, Object attributeValue) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeMapSet(transaction, this, attributeName, attributeValue);
                return null;
            });
        } else {
            this._prelimAttrs.put(attributeName, attributeValue);
        }
    }

    /**
     * 获取属性值
     *
     * @param attributeName 属性名
     * @return 属性值
     */
    public Object getAttribute(String attributeName) {
        return typeMapGet(this, attributeName);
    }

    /**
     * 检查属性是否存在
     *
     * @param attributeName 属性名
     * @return 是否存在
     */
    public boolean hasAttribute(String attributeName) {
        return typeMapHas(this, attributeName);
    }

    /**
     * 获取所有属性
     *
     * @param snapshot 快照
     * @return 属性键值对
     */
    public Map<String, Object> getAttributes(Snapshot snapshot) {
        return snapshot != null ? typeMapGetAllSnapshot(this, snapshot) : typeMapGetAll(this);
    }

    /**
     * 获取所有属性(无快照)
     *
     * @return 属性键值对
     */
    public Map<String, Object> getAttributes() {
        return getAttributes(null);
    }

    /**
     * 转换为DOM元素
     *
     * @param document DOM文档对象
     * @param hooks    钩子对象
     * @param binding  绑定对象
     * @return DOM元素节点
     */
    public Element toDOM(Document document, Map<String, Object> hooks, Object binding) {
        Element dom = document.createElement(this.nodeName);
        Map<String, Object> attrs = this.getAttributes();

        attrs.forEach((key, value) -> {
            if (value instanceof String) {
                dom.setAttribute(key, (String) value);
            }
        });

        typeListForEach(this, (yxml, index, type) -> {
            Node childNode = ((YXmlFragment) yxml).toDOM(document, hooks, binding);
            dom.appendChild(childNode);
        });

        // 处理绑定关联
        if (binding != null) {
            try {
                // 使用反射调用_createAssociation方法
                Method createAssoc = binding.getClass().getMethod("_createAssociation", Node.class, YXmlFragment.class);
                createAssoc.invoke(binding, dom, this);
            } catch (Exception e) {
                // 处理方法不存在的情况
                System.err.println("_createAssociation method not found in binding object");
            }
        }

        return dom;
    }

    @Override
    public void _write(UpdateEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlElementRefID);
        encoder.writeKey(this.nodeName);
    }

    public static AbstractType<?> readYXmlElement(UpdateDecoder decoder) {
        return new YXmlElement<>(decoder.readKey());
    }
}


