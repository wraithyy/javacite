package com.example;

/** Seeded PMD violations: public method without Javadoc and a non-final parameter. */
public final class Util {

    private Util() {}

    public static int twice(int n) {
        return n * 2;
    }
}
