package com.ai.myutils.decoder;


import java.util.Arrays;

/**
 * A Decoder handles the decoding of an Uint8Array.
 */
public class Decoder {
    public int[] arr;
    public int pos;

    /**
     * @param uint8Array {Uint8Array}  Binary data to decode
     */
    public Decoder(int[] uint8Array) {
        // Decoding target.
        this.arr = uint8Array;
        // Current decoding position.
        this.pos = 0;
    }

}

