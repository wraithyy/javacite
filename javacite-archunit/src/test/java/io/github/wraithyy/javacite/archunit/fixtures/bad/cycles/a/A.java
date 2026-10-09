package io.github.wraithyy.javacite.archunit.fixtures.bad.cycles.a;

import io.github.wraithyy.javacite.archunit.fixtures.bad.cycles.b.B;

public class A {
    public B b() {
        return new B();
    }
}
