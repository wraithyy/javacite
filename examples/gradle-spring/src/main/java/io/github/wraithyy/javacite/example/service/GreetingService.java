package io.github.wraithyy.javacite.example.service;

import io.github.wraithyy.javacite.example.repository.GreetingRepository;
import org.springframework.stereotype.Service;

/** Builds greetings from the templates held by the repository. */
@Service
public class GreetingService {

    private final GreetingRepository repository;

    /**
     * Creates the service.
     *
     * @param repository source of greeting templates
     */
    public GreetingService(final GreetingRepository repository) {
        this.repository = repository;
    }

    /**
     * Greets a person in the given language.
     *
     * @param name person to greet
     * @param language language code, for example {@code en}
     * @return the formatted greeting
     * @throws IllegalArgumentException when the language is not supported
     */
    public String greet(final String name, final String language) {
        return repository
                .findTemplate(language)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + language))
                .formatted(name);
    }
}
