package io.github.wraithyy.javacite.archunit.fixtures.bad.noClone;

public class Fixture implements Cloneable {
    @Override
    public Fixture clone() {
        return new Fixture();
    }
}
