package com.ai.myutils;

import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class Lists {

    @SafeVarargs
    public <K> List<K> of(K... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
}
