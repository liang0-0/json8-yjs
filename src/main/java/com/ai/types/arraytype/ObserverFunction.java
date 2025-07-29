package com.ai.types.arraytype;

// 辅助接口定义
public interface ObserverFunction<T, U> {
    void apply(T event, U transaction);
}
