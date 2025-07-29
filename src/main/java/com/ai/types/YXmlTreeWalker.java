package com.ai.types;

import com.ai.myutils.Maps;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate; /**
 * 表示 YXmlElement/YXmlFragment 节点的子集及其内部位置。
 * <p>
 * 可通过 {@link YXmlFragment#createTreeWalker} 创建
 */
public class YXmlTreeWalker implements Iterator<Object>, Iterable<Object> {
    private final Predicate<Object> _filter;
    private final Object _root;
    private Item _currentNode;
    private boolean _firstCall;

    public YXmlTreeWalker(Object root, Predicate<Object> f) {
        this._filter = f;
        this._root = root;
        this._currentNode = ((AbstractType<?>) root)._start;
        this._firstCall = true;
        if (((AbstractType<?>) root).doc == null) {
            AbstractType.warnPrematureAccess();
        }
    }

    public YXmlTreeWalker(Object root) {
        this(root, o -> true);
    }

    @Override
    public Iterator<Object> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        // 简单实现，实际应根据业务逻辑判断是否还有下一个节点
        return _currentNode != null;
    }

    @Override
    public Map<String, Object> next() {
        Item n = this._currentNode;
        Object type = n != null && n.content != null ? ((Map<String, Object>) n.content).get("type") : null;

        if (n != null && (!this._firstCall || n.deleted() || !this._filter.test(type))) {
            do {
                type = ((Map<String, Object>) n.content).get("type");
                if (!n.deleted() && (type.getClass() == YXmlElement.class ||
                        type.getClass() == YXmlFragment.class) &&
                        ((AbstractType<?>) type)._start != null) {
                    // 向下遍历树
                    n = ((AbstractType<?>) type)._start;
                } else {
                    // 向右或向上遍历树
                    while (n != null) {
                        Item nxt = n.next();
                        if (nxt != null) {
                            n = nxt;
                            break;
                        } else if (n.parent == this._root) {
                            n = null;
                        } else {
                            n = ((AbstractType<?>) n.parent)._item;
                        }
                    }
                }
            } while (n != null && (n.deleted() || !this._filter.test(((Map<String, Object>) n.content).get("type"))));
        }

        this._firstCall = false;
        if (n == null) {
            return Maps.of("value", null, "done", true);
        }
        this._currentNode = n;
        return Maps.of("value", ((Map<String, Object>) n.content).get("type"), "done", false);
    }
}
