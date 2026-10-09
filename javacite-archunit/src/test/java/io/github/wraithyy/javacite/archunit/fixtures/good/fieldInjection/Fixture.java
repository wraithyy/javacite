package io.github.wraithyy.javacite.archunit.fixtures.good.fieldInjection;

public class Fixture {
    public static class Dep {}

    public static class Holder {
        private final Dep dep;

        public Holder(Dep dep) {
            this.dep = dep;
        }
    }
}
