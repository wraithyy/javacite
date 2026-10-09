package io.github.wraithyy.javacite.core.config;

/** OWASP dependency-check settings. */
public record DependencyCheckOptions(double failOnCvss, InCheck inCheck) {

    /** When the audit joins the {@code check} lifecycle. */
    public enum InCheck {
        CI,
        ALWAYS,
        NEVER
    }

    public static DependencyCheckOptions defaults() {
        return new DependencyCheckOptions(7.0, InCheck.CI);
    }
}
