package io.github.wraithyy.javacite.archunit.fixtures.good.entityController;

import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

public class Fixture {
    @Entity
    public static class Person {}

    public record PersonDto(String name) {}

    @RestController
    public static class PersonController {
        public PersonDto one(PersonDto in) {
            return in;
        }
    }
}
