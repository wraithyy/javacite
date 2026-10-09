package io.github.wraithyy.javacite.archunit;

import io.github.wraithyy.javacite.core.config.ConfigException;
import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads rule on/off switches from javacite.yml so disabled rules are skipped instead of failed. */
public final class JavaciteRuleSwitches {

    private final ResolvedRules rules;

    private JavaciteRuleSwitches(ResolvedRules rules) {
        this.rules = rules;
    }

    /** Resolves switches from the system property javacite.config or by walking up from user.dir. */
    public static JavaciteRuleSwitches current() {
        return from(locate(System.getProperty("javacite.config"), System.getProperty("user.dir")));
    }

    /** Package-private test seam: explicit config path, or null for defaults. */
    static JavaciteRuleSwitches from(Path config) {
        try {
            var cfg = config == null ? ConfigLoader.defaults() : ConfigLoader.load(Files.readString(config));
            // Spring rules are only evaluated when the user opted in by including JavaciteSpringRules.
            return new JavaciteRuleSwitches(ResolvedRules.of(cfg, true));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + config, e);
        } catch (ConfigException e) {
            throw new IllegalStateException("Invalid javacite.yml at " + config + ": " + e.getMessage(), e);
        }
    }

    static Path locate(String configProperty, String userDir) {
        String start = configProperty != null && !configProperty.isBlank() ? configProperty : userDir;
        if (start == null) {
            return null;
        }
        Path p = Path.of(start).toAbsolutePath();
        if (Files.isRegularFile(p)) {
            return p;
        }
        for (Path dir = p; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("javacite.yml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isEnabled(String id) {
        return rules.isEnabled(id);
    }
}
