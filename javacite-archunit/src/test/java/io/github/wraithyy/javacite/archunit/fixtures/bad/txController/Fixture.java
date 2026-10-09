package io.github.wraithyy.javacite.archunit.fixtures.bad.txController;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

public class Fixture {
    @RestController
    public static class ClassLevel {
        @Transactional
        public void write() {}
    }
}
