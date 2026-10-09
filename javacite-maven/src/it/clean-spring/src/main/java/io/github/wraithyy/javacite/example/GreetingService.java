package io.github.wraithyy.javacite.example;

import org.springframework.stereotype.Service;

/** Renders greetings from templates held by the repository. */
@Service
public class GreetingService {

    private final GreetingRepository repository;

    /**
     * Creates the service.
     *
     * @param greetingRepository template source
     */
    public GreetingService(final GreetingRepository greetingRepository) {
        this.repository = greetingRepository;
    }

    /**
     * Greets a person in the given language.
     *
     * @param name who to greet
     * @param language template language code
     * @return the rendered greeting
     * @throws IllegalArgumentException when the language has no template
     */
    public String greet(final String name, final String language) {
        return repository
                .findTemplate(language)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + language))
                .formatted(name);
    }
}
