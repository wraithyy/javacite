package com.example;

/** Seeded NullAway violation: returns null from a non-null method. */
final class Broken {

    private Broken() {}

    static String name() {
        return null;
    }
}
