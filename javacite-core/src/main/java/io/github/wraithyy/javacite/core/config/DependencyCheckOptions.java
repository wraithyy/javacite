package io.github.wraithyy.javacite.core.config;

/** OWASP dependency-check settings. */
public record DependencyCheckOptions(double failOnCvss, InCheck inCheck) {

    /** When the audit joins the {@code check} lifecycle. */
    public enum InCheck {
        CI,
        ALWAYS,
        NEVER
    }

    /** CI counts as set when non-blank and not {@code false}/{@code 0}; shared by the Gradle and Maven wiring. */
    public static boolean isCi(String ciEnv) {
        if (ciEnv == null) {
            return false;
        }
        String v = ciEnv.trim();
        return !v.isEmpty() && !v.equalsIgnoreCase("false") && !v.equals("0");
    }

    public static DependencyCheckOptions defaults() {
        return new DependencyCheckOptions(7.0, InCheck.CI);
    }
}
