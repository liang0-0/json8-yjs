package com.ai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 通用事件处理器实现
 * @param <ARG0> 第一个事件参数类型
 * @param <ARG1> 第二个事件参数类型
 */
public class EventHandler<ARG0, ARG1> {

    /**
     * 监听器列表
     */
    public final List<BiConsumer<ARG0, ARG1>> l;

    public EventHandler() {
        this.l = new ArrayList<>();
    }

    /**
     * 创建新的事件处理器
     * @return 新的事件处理器实例
     */
    public static <ARG0, ARG1> EventHandler<ARG0, ARG1> createEventHandler() {
        return new EventHandler<>();
    }


    /**
     * 添加事件监听器
     * @param eventHandler 目标事件处理器
     * @param f 要添加的监听器函数
     */
    public static <ARG0, ARG1> void addEventHandlerListener(EventHandler<ARG0, ARG1> eventHandler,
                                                           BiConsumer<ARG0, ARG1> f) {
        eventHandler.l.add(f);
    }

    /**
     * 移除事件监听器
     * @param eventHandler 目标事件处理器
     * @param f 要移除的监听器函数
     */
    public static <ARG0, ARG1> void removeEventHandlerListener(EventHandler<ARG0, ARG1> eventHandler,
                                                              BiConsumer<ARG0, ARG1> f) {
        boolean removed = eventHandler.l.remove(f);
        if (!removed) {
            System.err.println("[yjs] Tried to remove event handler that doesn't exist.");
        }
    }

    /**
     * 移除所有事件监听器
     * @param eventHandler 目标事件处理器
     */
    public static <ARG0, ARG1> void removeAllEventHandlerListeners(EventHandler<ARG0, ARG1> eventHandler) {
        eventHandler.l.clear();
    }

    /**
     * 调用所有事件监听器
     * @param eventHandler 目标事件处理器
     * @param arg0 第一个事件参数
     * @param arg1 第二个事件参数
     */
    public static <ARG0, ARG1> void callEventHandlerListeners(EventHandler<ARG0, ARG1> eventHandler, ARG0 arg0, ARG1 arg1) {
        for (BiConsumer<ARG0, ARG1> listener : eventHandler.l) {
            listener.accept(arg0, arg1);
        }
    }
}