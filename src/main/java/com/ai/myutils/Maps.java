package com.ai.myutils;

import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class Maps {

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> of(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("键值对的数量必须为偶数");
        }
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((K) keysAndValues[i], (V) keysAndValues[i + 1]);
        }
        return map;
    }
}
