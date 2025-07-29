package com.ai.myutils.encoder;

public class IntDiffOptRleEncoder {
    private Encoder encoder;
    private int state;
    private int count;
    private int diff;

    public IntDiffOptRleEncoder() {
        this.encoder = new Encoder();
        this.state = 0;
        this.count = 0;
        this.diff = 0;
    }

    public void write(int value) {
        if (diff == value - state) {
            state = value;
            count++;
        } else {
            flush();
            count = 1;
            diff = value - state;
            state = value;
        }
    }

    private void flush() {
        if (count > 0) {
            int encodedDiff = diff * 2 + (count == 1 ? 0 : 1);
            encoding.writeVarInt(encoder, encodedDiff, encodedDiff < 0);
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
