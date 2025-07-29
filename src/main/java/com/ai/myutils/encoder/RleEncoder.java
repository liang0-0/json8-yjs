package com.ai.myutils.encoder;

public class RleEncoder extends Encoder {
    private final EncoderFunction<Integer> writer;
    private Object state;
    private int count;

    public RleEncoder(EncoderFunction<Integer> writer) {
        this.writer = writer;
        this.state = null;
        this.count = 0;
    }

    public void write(int value) {
        if (state != null && state.equals(value)) {
            count++;
        } else {
            if (count > 0) {
                encoding.writeVarUint(this, count - 1);
            }
            count = 1;
            writer.apply(this, value);
            state = value;
        }
    }
}
