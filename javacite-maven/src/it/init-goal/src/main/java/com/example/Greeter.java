package com.example;

/** Minimal clean class. */
public final class Greeter {

    private Greeter() {}

    /**
     * Greets.
     *
     * @param name who to greet
     * @return the greeting
     */
    public static String greet(final String name) {
        return "Hello " + name;
    }
}
