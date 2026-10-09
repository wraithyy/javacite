package io.github.wraithyy.javacite.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.junit.ArchTest;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaciteRuleSwitchesTest {

    private static final String BAD = "io.github.wraithyy.javacite.archunit.fixtures.bad.systemOut";

    @TempDir Path tmp;

    @Test
    void disabledRuleIsSkippedOnBadFixture() throws Exception {
        Path cfg = tmp.resolve("javacite.yml");
        Files.writeString(cfg, "version: 1\nrules:\n  archunit.NoSystemOut: off\n");
        var sw = JavaciteRuleSwitches.from(cfg);
        assertThat(sw.isEnabled("archunit.NoSystemOut")).isFalse();
        assertThat(sw.isEnabled("archunit.NoFieldInjection")).isTrue();

        var classes = new ClassFileImporter().importPackages(BAD);
        assertThat(JavaciteRules.noSystemOut(sw).evaluate(classes).hasViolation()).isFalse();
        JavaciteRules.noSystemOut(sw).check(classes);
    }

    @Test
    void absentConfigMeansDefaults() {
        assertThat(JavaciteRuleSwitches.from(null).isEnabled("archunit.NoSystemOut")).isTrue();
    }

    @Test
    void locatesConfigByWalkingUp() throws Exception {
        Path cfg = tmp.resolve("javacite.yml");
        Files.writeString(cfg, "version: 1\n");
        Path deep = Files.createDirectories(tmp.resolve("a/b/c"));
        assertThat(JavaciteRuleSwitches.locate(null, deep.toString())).isEqualTo(cfg);
        assertThat(JavaciteRuleSwitches.locate(deep.toString(), "/nonexistent-dir")).isEqualTo(cfg);
        assertThat(JavaciteRuleSwitches.locate(cfg.toString(), "/nonexistent-dir")).isEqualTo(cfg);
    }

    @Test
    void publicFieldsAreArchTests() {
        for (Class<?> c : new Class<?>[] {JavaciteRules.class, JavaciteSpringRules.class}) {
            int n = 0;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isPublic(f.getModifiers()) && ArchRule.class.isAssignableFrom(f.getType())) {
                    assertThat(f.isAnnotationPresent(ArchTest.class)).as(f.getName()).isTrue();
                    n++;
                }
            }
            assertThat(n).as(c.getSimpleName()).isGreaterThanOrEqualTo(5);
        }
    }
}
