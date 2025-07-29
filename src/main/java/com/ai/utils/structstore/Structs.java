package com.ai.utils.structstore;

import java.util.Map;


public class Structs {
    public final Map<Integer, Integer> missing;
    public int[] update;

    public Structs(Map<Integer, Integer> missing, int[] update) {
        this.missing = missing;
        this.update = update;
    }
}