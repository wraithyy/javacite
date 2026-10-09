package io.github.wraithyy.javacite.archunit.fixtures.good.layered;

import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

public class Fixture {
    @Repository
    public static class ThingRepository {}

    @Service
    public static class ThingService {
        private final ThingRepository repo = new ThingRepository();

        public ThingRepository repo() {
            return repo;
        }
    }

    @RestController
    public static class ThingController {
        private final ThingService service = new ThingService();

        public ThingService service() {
            return service;
        }
    }
}
