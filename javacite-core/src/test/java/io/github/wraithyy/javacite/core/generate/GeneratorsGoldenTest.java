package io.github.wraithyy.javacite.core.generate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Set -Dgolden.update=true to rewrite golden files into src/test/resources/golden. */
class GeneratorsGoldenTest {

    private static final String OVERRIDES =
            """
            rules:
              pmd.LocalVariableCouldBeFinal: off
              errorprone.HidingField: warn
              pmd.CloseResource: warn
              pmd.UnusedPrivateField: off
              errorprone.MissingOverride: warn
              errorprone.UnusedVariable: off
            tools:
              nullaway: { mode: annotatedPackages, packages: [com.acme, org.x] }
            """;

    static JavaciteConfig defaults() {
        return ConfigLoader.defaults();
    }

    static JavaciteConfig overrides() {
        return ConfigLoader.load(OVERRIDES);
    }

    @Test
    void pmdDefault() throws IOException {
        check("pmd-default.xml", new PmdRulesetGenerator().generate(ResolvedRules.of(defaults())));
    }

    @Test
    void pmdOverrides() throws IOException {
        check("pmd-overrides.xml", new PmdRulesetGenerator().generate(ResolvedRules.of(overrides())));
    }

    @Test
    void errorproneDefault() throws IOException {
        JavaciteConfig c = defaults();
        check("errorprone-default.txt", String.join("\n", new ErrorProneArgsGenerator().args(ResolvedRules.of(c), c)) + "\n");
    }

    @Test
    void errorproneOverrides() throws IOException {
        JavaciteConfig c = overrides();
        check("errorprone-overrides.txt", String.join("\n", new ErrorProneArgsGenerator().args(ResolvedRules.of(c), c)) + "\n");
    }

    private static void check(String name, String actual) throws IOException {
        if (Boolean.getBoolean("golden.update")) {
            Path p = Path.of("src/test/resources/golden", name);
            Files.createDirectories(p.getParent());
            Files.writeString(p, actual, StandardCharsets.UTF_8);
        }
        try (InputStream in = GeneratorsGoldenTest.class.getResourceAsStream("/golden/" + name)) {
            assertThat(in).as("golden file %s", name).isNotNull();
            assertThat(actual).isEqualTo(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
