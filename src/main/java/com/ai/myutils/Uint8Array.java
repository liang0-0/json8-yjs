package com.ai.myutils;


public class Uint8Array {

    public static int[] toIntArray(byte[] update) {
        int[] data = new int[update.length];
        for (int i = 0; i < update.length; i++) {
            data[i] = Byte.toUnsignedInt(update[i]);
        }
        return data;
    }

    public static byte[] toByteArray(int[] update) {
        byte[] data = new byte[update.length];
        for (int i = 0; i < update.length; i++) {
            data[i] = (byte) update[i];
        }
        return data;
    }

    public static void printUint8Array(int[] array) {
        System.out.print("Uint8Array(" + array.length + ") [");
        if (array.length == 0) {
            System.out.println("]");
            return;
        }

        int elementsPerLine = 4;
        if (array.length > 80) elementsPerLine = 12;
        else if (array.length  > 50) elementsPerLine = 11;
        else if (array.length  >= 40) elementsPerLine = 9;
        else if (array.length > 30) elementsPerLine = 7;
        else if (array.length > 20) elementsPerLine = 7;
        else if (array.length == 18) elementsPerLine = 6;
        else if (array.length > 15) elementsPerLine = 7;
        else if (array.length >= 13) elementsPerLine = 6;
        else if (array.length > 10) elementsPerLine = 5;

        if (array.length > 6) System.out.print("\n ");
        for (int i = 0; i < array.length; i++) {
            int padding = array.length <= 6 ? 2 : array.length < 12 ? 3 : 4;
            System.out.printf("%" + padding + "d", array[i]);
            if (i < array.length - 1) System.out.print(",");
            if ((i + 1) % elementsPerLine == 0 && (i + 1) < array.length) System.out.print("\n ");
        }
        if (array.length > 6) System.out.println();
        System.out.println(" ]");
    }
}