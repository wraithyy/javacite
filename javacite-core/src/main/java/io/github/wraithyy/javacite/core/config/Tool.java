package io.github.wraithyy.javacite.core.config;

import java.util.Arrays;
import java.util.Optional;

/** Tools that can be switched on or off under {@code tools:} (Sonar has its own {@link SonarMode}). */
public enum Tool {
    SPOTLESS("spotless"),
    ERRORPRONE("errorprone"),
    NULLAWAY("nullaway"),
    PMD("pmd"),
    ARCHUNIT("archunit"),
    JACOCO("jacoco"),
    DEPENDENCY_CHECK("dependencyCheck"),
    ENFORCER("enforcer");

    private final String key;

    Tool(String key) {
        this.key = key;
    }

    /** The key used in javacite.yml and in rules-registry.yml. */
    public String key() {
        return key;
    }

    public static Optional<Tool> fromKey(String key) {
        return Arrays.stream(values()).filter(t -> t.key.equals(key)).findFirst();
    }
}
