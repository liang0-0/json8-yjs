package com.ai.myutils;

public class Printer {

    public static void print(Object... args) {
        for (Object arg : args) {
            System.out.print(arg);
            System.out.print(" ");
        }
        System.out.println();
    }
}
