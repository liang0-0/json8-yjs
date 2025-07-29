package com.ai.types;

import java.util.*;
import java.util.function.Function;

import com.ai.structs.ContentType;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import org.w3c.dom.*;

/**
 * YXmlHook类允许管理自定义类型的绑定
 * 继承自YMap，用于处理XML钩子节点
 */
public class YXmlHook extends YMap {
    public String hookName;

    /**
     * 构造函数
     * @param hookName DOM节点的nodeName
     */
    public YXmlHook(String hookName) {
        super();
        this.hookName = hookName;
    }

    /**
     * 创建具有相同效果的Item副本（不包含位置效果）
     * @return 新的YXmlHook实例
     */
    @Override
    public YXmlHook _copy() {
        return new YXmlHook(this.hookName);
    }

    /**
     * 创建此数据类型的副本
     * 注意：内容只有在被包含到Ydoc中后才可读
     * @return 克隆后的YXmlHook实例
     */
    @Override
    public YXmlHook clone() {
        YXmlHook el = new YXmlHook(this.hookName);
        this.forEach((key, value) -> {
            el.set((String) key, value);
        });
        return el;
    }

    /**
     * 创建镜像此YXmlElement的DOM元素
     * @param document 文档对象（在nodejs中调用时必须定义）
     * @param hooks 可选属性，用于自定义钩子在DOM中的呈现方式
     * @param binding 不应设置此属性，用于DomBinding创建关联
     * @return DOM元素
     */
    public Element toDOM(Document document, Map<String, Object> hooks, Object binding) {
        Object hook = hooks != null ? hooks.get(this.hookName) : null;
        Element dom;
        
        if (hook != null) {
            // 假设hook对象有createDom方法
            dom = ((Map<String, Function<YXmlHook, Element>>)hook).get("createDom").apply(this);
        } else {
            dom = document.createElement(this.hookName);
        }
        
        dom.setAttribute("data-yjs-hook", this.hookName);
        
        if (binding != null) {
            // 假设binding对象有_createAssociation方法
            try {
                java.lang.reflect.Method method = binding.getClass().getMethod("_createAssociation", Element.class, YXmlHook.class);
                method.invoke(binding, dom, this);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create association", e);
            }
        }
        
        return dom;
    }

    /**
     * 重载方法，简化调用
     * @return DOM元素
     */
    public Element toDOM() {
        return toDOM(getDefaultDocument(), null, null);
    }

    /**
     * 将属性转换为二进制并写入编码器
     * 当此项发送到远程对等点时调用
     * @param encoder 用于写入数据的编码器
     */
    @Override
    public void _write(UpdateEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlHookRefID);
        encoder.writeKey(this.hookName);
    }

    /**
     * 获取默认文档对象
     * @return 文档对象
     */
    private static Document getDefaultDocument() {
        try {
            // 使用DOM解析器创建文档
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create document", e);
        }
    }

    /**
     * 从解码器读取YXmlHook
     * @param decoder 解码器
     * @return 新的YXmlHook实例
     */
    public static YXmlHook readYXmlHook(UpdateDecoder decoder) {
        return new YXmlHook(decoder.readKey());
    }
}