package io.github.wraithyy.javacite.archunit.fixtures.good.genericExceptions;

public class Fixture {
    public void fail() {
        throw new IllegalStateException("boom");
    }
}
