package com.ai.myutils.encoder;

public class RleIntDiffEncoder extends Encoder {
    private int state;
    private int count;

    public RleIntDiffEncoder(int start) {
        this.state = start;
        this.count = 0;
    }

    public void write(int value) {
        if (state == value && count > 0) {
            count++;
        } else {
            if (count > 0) {
                encoding.writeVarUint(this, count - 1);
            }
            count = 1;
            encoding.writeVarInt(this, value - state, value - state < 0);
            state = value;
        }
    }
}
