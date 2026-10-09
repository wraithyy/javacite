package io.github.wraithyy.javacite.example.web;

import io.github.wraithyy.javacite.example.service.GreetingService;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoint for greetings. */
@RestController
public class GreetingController {

    private final GreetingService service;

    /**
     * Creates the controller.
     *
     * @param service greeting service
     */
    public GreetingController(final GreetingService service) {
        this.service = service;
    }

    /**
     * Returns a greeting.
     *
     * @param name person to greet
     * @param lang language code
     * @return the greeting text
     */
    @GetMapping("/greeting")
    public String greeting(@RequestParam final String name, @RequestParam(defaultValue = "en") final String lang) {
        return service.greet(name, lang);
    }

    /**
     * Maps an unsupported language to a 400 response.
     *
     * @param e the failure
     * @return the error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(final IllegalArgumentException e) {
        return Objects.requireNonNullElse(e.getMessage(), "Bad request");
    }
}
