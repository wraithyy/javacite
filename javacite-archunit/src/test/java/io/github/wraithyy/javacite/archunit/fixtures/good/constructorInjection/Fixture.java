package io.github.wraithyy.javacite.archunit.fixtures.good.constructorInjection;

import org.springframework.beans.factory.annotation.Autowired;

public class Fixture {
    public static class Dep {}

    public static class Holder {
        private final Dep dep;

        @Autowired
        public Holder(Dep dep) {
            this.dep = dep;
        }
    }
}
