package io.github.wraithyy.javacite.archunit.fixtures.bad.entityController;

import jakarta.persistence.Entity;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

public class Fixture {
    @Entity
    public static class Person {}

    @RestController
    public static class PersonController {
        public List<Person> all() {
            return List.of();
        }
    }
}
