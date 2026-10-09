package io.github.wraithyy.javacite.example.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory store of greeting templates keyed by language code. */
@Repository
public class GreetingRepository {

    private final Map<String, String> templates =
            new ConcurrentHashMap<>(Map.of("en", "Hello, %s!", "cs", "Ahoj, %s!"));

    /**
     * Looks up a template.
     *
     * @param language language code
     * @return the template, or empty when the language is unknown
     */
    public Optional<String> findTemplate(final String language) {
        return Optional.ofNullable(templates.get(language));
    }
}
