package io.github.wraithyy.javacite.archunit.fixtures.bad.genericExceptions;

public class Fixture {
    public void fail() {
        throw new RuntimeException("boom");
    }
}
