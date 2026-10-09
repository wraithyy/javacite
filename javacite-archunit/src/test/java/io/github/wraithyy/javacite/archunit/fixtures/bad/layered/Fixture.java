package io.github.wraithyy.javacite.archunit.fixtures.bad.layered;

import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

public class Fixture {
    @Repository
    public static class ThingRepository {}

    @RestController
    public static class ThingController {
        private final ThingRepository repo = new ThingRepository();

        public ThingRepository repo() {
            return repo;
        }
    }
}
