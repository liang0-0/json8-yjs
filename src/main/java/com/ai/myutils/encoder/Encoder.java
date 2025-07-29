package com.ai.myutils.encoder;

import java.util.ArrayList;
import java.util.List;

public class Encoder {
    protected int cpos;
    protected int[] cbuf;
    protected List<int[]> bufs;

    public Encoder() {
        this.cpos = 0;
        this.cbuf = new int[100];
        this.bufs = new ArrayList<>();
    }
}



