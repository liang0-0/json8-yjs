package com.ai.myutils.encoder;

public class IntDiffEncoder extends Encoder {
    private int state;

    public IntDiffEncoder(int start) {
        this.state = start;
    }

    public void write(int value) {
        encoding.writeVarInt(this, value - state, value - state < 0);
        state = value;
    }
}
