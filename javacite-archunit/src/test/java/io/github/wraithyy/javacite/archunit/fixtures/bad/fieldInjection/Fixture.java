package io.github.wraithyy.javacite.archunit.fixtures.bad.fieldInjection;

import org.springframework.beans.factory.annotation.Autowired;

public class Fixture {
    public static class Dep {}

    public static class Holder {
        @Autowired private Dep dep;
    }
}
