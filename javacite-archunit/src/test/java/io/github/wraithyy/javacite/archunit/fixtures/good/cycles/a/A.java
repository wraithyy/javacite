package io.github.wraithyy.javacite.archunit.fixtures.good.cycles.a;

import io.github.wraithyy.javacite.archunit.fixtures.good.cycles.b.B;

public class A {
    public B b() {
        return new B();
    }
}
