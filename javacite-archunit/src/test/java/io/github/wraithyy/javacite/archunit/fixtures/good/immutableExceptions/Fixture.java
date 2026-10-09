package io.github.wraithyy.javacite.archunit.fixtures.good.immutableExceptions;

public class Fixture extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;

    public Fixture(int code) {
        this.code = code;
    }
}
