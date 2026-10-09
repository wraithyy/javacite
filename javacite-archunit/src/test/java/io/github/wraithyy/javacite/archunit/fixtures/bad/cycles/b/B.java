package io.github.wraithyy.javacite.archunit.fixtures.bad.cycles.b;

import io.github.wraithyy.javacite.archunit.fixtures.bad.cycles.a.A;

public class B {
    public A a() {
        return new A();
    }
}
