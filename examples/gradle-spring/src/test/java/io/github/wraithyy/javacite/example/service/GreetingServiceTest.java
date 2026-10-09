package io.github.wraithyy.javacite.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wraithyy.javacite.example.repository.GreetingRepository;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link GreetingService}. */
class GreetingServiceTest {

    private final GreetingService service = new GreetingService(new GreetingRepository());

    @Test
    void greetsInEnglish() {
        assertThat(service.greet("Ann", "en")).isEqualTo("Hello, Ann!");
    }

    @Test
    void rejectsUnknownLanguage() {
        assertThatThrownBy(() -> service.greet("Ann", "xx")).isInstanceOf(IllegalArgumentException.class);
    }
}
