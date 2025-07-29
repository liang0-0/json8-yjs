package com.ai.types;

import com.ai.types.arraytype.AbstractType;
import com.ai.utils.Transaction;
import com.ai.utils.YEvent;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * XML元素或片段变更事件类
 * 描述YXmlElement、YXmlText或YXmlFragment上的变更
 */
public class YXmlEvent extends YEvent<AbstractType<?>> {

    // 子节点列表是否变更
    private boolean childListChanged;
    // 变更的属性集合
    private Set<String> attributesChanged;

    /**
     * 构造函数
     * @param target 事件目标对象(YXmlElement|YXmlText|YXmlFragment)
     * @param subs 变更的属性集合，包含null表示子列表变更
     * @param transaction 关联的事务对象
     */
    public YXmlEvent(AbstractType<?> target, Set<String> subs, Transaction transaction) {
        super(target, transaction);
        this.childListChanged = false;
        this.attributesChanged = new LinkedHashSet<>();
        
        // 处理变更集合
        for (String sub : subs) {
            if (sub == null) {
                this.childListChanged = true;
            } else {
                this.attributesChanged.add(sub);
            }
        }
    }

    /**
     * 获取子节点列表是否变更
     * @return 是否变更
     */
    public boolean isChildListChanged() {
        return childListChanged;
    }

    /**
     * 获取变更的属性集合
     * @return 属性名集合
     */
    public Set<String> getAttributesChanged() {
        return attributesChanged;
    }

    /**
     * 检查指定属性是否变更
     * @param attributeName 属性名
     * @return 是否变更
     */
    public boolean isAttributeChanged(String attributeName) {
        return attributesChanged.contains(attributeName);
    }
}