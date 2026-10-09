package io.github.wraithyy.javacite.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for the greeting rules. */
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
