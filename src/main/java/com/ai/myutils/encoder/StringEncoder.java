package com.ai.myutils.encoder;

import java.util.ArrayList;
import java.util.List;

public class StringEncoder {
    private List<String> strings;
    private StringBuilder buffer;
    private UintOptRleEncoder lengthEncoder;

    public StringEncoder() {
        this.strings = new ArrayList<>();
        this.buffer = new StringBuilder();
        this.lengthEncoder = new UintOptRleEncoder();
    }

    public void write(String str) {
        buffer.append(str);
        if (buffer.length() > 19) {
            strings.add(buffer.toString());
            buffer.setLength(0);
        }
        lengthEncoder.write(str.length());
    }

    public int[] toUint8Array() {
        Encoder encoder = new Encoder();
        strings.add(buffer.toString());
        buffer.setLength(0);
        encoding.writeVarString(encoder, String.join("", strings));
        encoding.writeUint8Array(encoder, lengthEncoder.toUint8Array());
        return encoding.toUint8Array(encoder);
    }
}
