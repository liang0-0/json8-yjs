package com.ai.myutils.decoder;

import static com.ai.myutils.decoder.decoding.readVarString;

public class StringDecoder {
    private final UintOptRleDecoder decoder;
    private final String str;
    private int spos;

    public StringDecoder(int[] uint8Array) {
        this.decoder = new UintOptRleDecoder(uint8Array);
        this.str = readVarString(this.decoder);
        this.spos = 0;
    }

    public String read() {
        int end = this.spos + this.decoder.read();
        String res = this.str.substring(this.spos, end);
        this.spos = end;
        return res;
    }
}
