package io.github.wraithyy.javacite.core.config;

/** Thrown when javacite.yml or the rule registry is malformed. */
public final class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
