package io.github.wraithyy.javacite.archunit.fixtures.good.noClone;

public class Fixture {
    public Fixture copy() {
        return new Fixture();
    }
}
