package io.github.wraithyy.javacite.archunit.fixtures.bad.constructorInjection;

import org.springframework.beans.factory.annotation.Autowired;

public class Fixture {
    public static class Dep {}

    public static class Holder {
        private Dep dep;

        @Autowired
        public void setDep(Dep dep) {
            this.dep = dep;
        }

        public Dep dep() {
            return dep;
        }
    }
}
