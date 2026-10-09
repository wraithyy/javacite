package io.github.wraithyy.javacite.example;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry point for greetings. */
@RestController
public class GreetingController {

    private final GreetingService service;

    /**
     * Creates the controller.
     *
     * @param greetingService greeting logic
     */
    public GreetingController(final GreetingService greetingService) {
        this.service = greetingService;
    }

    /**
     * Greets a person.
     *
     * @param name who to greet
     * @param lang template language code
     * @return the rendered greeting
     */
    @GetMapping("/greeting")
    public String greeting(@RequestParam final String name, @RequestParam(defaultValue = "en") final String lang) {
        return service.greet(name, lang);
    }

    /**
     * Maps an unsupported language to a 400 response.
     *
     * @param e the failure
     * @return the response body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(final IllegalArgumentException e) {
        return Objects.requireNonNullElse(e.getMessage(), "Bad request");
    }
}
