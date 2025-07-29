package com.ai.utils;

import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;

/**
 * 检查parent是否是child的父级
 */
public class IsParentOf {

    /**
     * 检查parent是否是child的父级
     * @param parent 要检查的父级类型
     * @param child 要检查的子项
     * @return 如果parent是child的父级则返回true
     */
    public static boolean isParentOf(AbstractType<?> parent, Item child) {
        while (child != null) {
            if (child.parent == parent) {
                return true;
            }
            child = ((AbstractType<?>) child.parent)._item;
        }
        return false;
    }
}