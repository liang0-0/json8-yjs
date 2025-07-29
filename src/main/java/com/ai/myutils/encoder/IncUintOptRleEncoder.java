package com.ai.myutils.encoder;

public class IncUintOptRleEncoder {
    private Encoder encoder;
    private int state;
    private int count;

    public IncUintOptRleEncoder() {
        this.encoder = new Encoder();
        this.state = 0;
        this.count = 0;
    }

    public void write(int value) {
        if (state + count == value) {
            count++;
        } else {
            flush();
            count = 1;
            state = value;
        }
    }

    private void flush() {
        if (count > 0) {
            encoding.writeVarInt(encoder, count == 1 ? state : -state, count != 1);
            if (count > 1) {
                encoding.writeVarUint(encoder, count - 2);
            }
        }
    }

    public int[] toUint8Array() {
        flush();
        return encoding.toUint8Array(encoder);
    }
}
