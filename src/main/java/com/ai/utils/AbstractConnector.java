package com.ai.utils;

import com.ai.myutils.observable.ObservableV2;

/**
 * 这是一个抽象接口，所有连接器都应实现此接口以保持可互换性。
 * 
 * @注意 这个接口是实验性的，不建议实际继承这个类。
 *       它仅作为类型信息使用。
 */
public abstract class AbstractConnector extends ObservableV2 {
    /**
     * Yjs文档实例
     */
    public Doc doc;
    
    /**
     * 感知对象
     */
    public Object awareness;

    /**
     * 构造函数
     * @param ydoc Yjs文档实例
     * @param awareness 感知对象
     */
    public AbstractConnector(Doc ydoc, Object awareness) {
        super();
        this.doc = ydoc;
        this.awareness = awareness;
    }
}