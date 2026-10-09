package io.github.wraithyy.javacite.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

    @Test
    void defaultsEnableEverythingStrictly() {
        JavaciteConfig c = ConfigLoader.defaults();

        assertThat(c.java()).isEqualTo(17);
        for (Tool t : Tool.values()) {
            assertThat(c.tool(t).enabled()).as(t.key()).isTrue();
        }
        assertThat(c.nullaway().kind()).isEqualTo(NullawayMode.Kind.ONLY_NULL_MARKED);
        assertThat(c.jacocoMin()).isEqualTo(0.80);
        assertThat(c.dependencyCheck()).isEqualTo(new DependencyCheckOptions(7.0, DependencyCheckOptions.InCheck.CI));
        assertThat(c.sonar()).isEqualTo(SonarMode.AUTO);
        assertThat(c.spring()).isEqualTo(SpringMode.AUTO);
        assertThat(c.rules()).isEmpty();
        assertThat(c.bannedDeps()).contains("commons-lang:commons-lang", "log4j:log4j");
        assertThat(c.agentTargets()).contains("agents-md", "claude-hooks", "git-hooks");
    }

    @Test
    void emptyDocumentYieldsDefaults() {
        assertThat(ConfigLoader.load("")).isEqualTo(ConfigLoader.defaults());
        assertThat(ConfigLoader.load("version: 1")).isEqualTo(ConfigLoader.defaults());
    }

    @Test
    void toolOnOffAndBooleansParse() {
        JavaciteConfig c = ConfigLoader.load("""
                version: 1
                tools:
                  pmd: off
                  archunit: true
                """);

        assertThat(c.tool(Tool.PMD)).isInstanceOf(ToolSetting.Off.class);
        assertThat(c.tool(Tool.ARCHUNIT).enabled()).isTrue();
        assertThat(c.tool(Tool.ERRORPRONE).enabled()).isTrue();
    }

    @Test
    void nullawayObjectVariants() {
        JavaciteConfig a = ConfigLoader.load("tools:\n  nullaway: { mode: onlyNullMarked }");
        assertThat(a.nullaway().kind()).isEqualTo(NullawayMode.Kind.ONLY_NULL_MARKED);
        assertThat(a.tool(Tool.NULLAWAY)).isInstanceOf(ToolSetting.Options.class);

        JavaciteConfig b = ConfigLoader.load("tools:\n  nullaway: { mode: annotatedPackages, packages: [com.acme, org.x] }");
        assertThat(b.nullaway().kind()).isEqualTo(NullawayMode.Kind.ANNOTATED_PACKAGES);
        assertThat(b.nullaway().packages()).containsExactly("com.acme", "org.x");
    }

    @Test
    void nullawayAnnotatedPackagesRequiresPackages() {
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  nullaway: { mode: annotatedPackages }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("packages");
    }

    @Test
    void jacocoDependencyCheckAndSpotlessOptions() {
        JavaciteConfig c = ConfigLoader.load("""
                tools:
                  jacoco: { min: 0.9 }
                  dependencyCheck: { failOnCvss: 9.5, inCheck: always }
                  spotless: { ratchetFrom: origin/main }
                """);

        assertThat(c.jacocoMin()).isEqualTo(0.9);
        assertThat(c.dependencyCheck().failOnCvss()).isEqualTo(9.5);
        assertThat(c.dependencyCheck().inCheck()).isEqualTo(DependencyCheckOptions.InCheck.ALWAYS);
        assertThat(c.tool(Tool.SPOTLESS)).isInstanceOf(ToolSetting.Options.class);
        assertThat(((ToolSetting.Options) c.tool(Tool.SPOTLESS)).values()).containsEntry("ratchetFrom", "origin/main");
    }

    @Test
    void integerNumbersAcceptedForCvss() {
        assertThat(ConfigLoader.load("tools:\n  dependencyCheck: { failOnCvss: 8 }")
                        .dependencyCheck()
                        .failOnCvss())
                .isEqualTo(8.0);
    }

    @Test
    void sonarAndSpringModes() {
        JavaciteConfig c = ConfigLoader.load("tools:\n  sonar: on\nspring: off");
        assertThat(c.sonar()).isEqualTo(SonarMode.ON);
        assertThat(c.spring()).isEqualTo(SpringMode.OFF);
        assertThat(ConfigLoader.load("tools:\n  sonar: off\nspring: on").sonar()).isEqualTo(SonarMode.OFF);
    }

    @Test
    void ruleOverridesDepsAndAgentsParse() {
        JavaciteConfig c = ConfigLoader.load("""
                version: 1
                java: 21
                rules:
                  pmd.LocalVariableCouldBeFinal: off
                  pmd.CloseResource: warn
                  archunit.NoFieldInjection: error
                deps:
                  ban: ["a:b"]
                agents:
                  targets: [agents-md, cursor]
                """);

        assertThat(c.java()).isEqualTo(21);
        assertThat(c.rules())
                .containsEntry("pmd.LocalVariableCouldBeFinal", RuleLevel.OFF)
                .containsEntry("pmd.CloseResource", RuleLevel.WARN)
                .containsEntry("archunit.NoFieldInjection", RuleLevel.ERROR);
        assertThat(c.bannedDeps()).containsExactly("a:b");
        assertThat(c.agentTargets()).containsExactly("agents-md", "cursor");
    }

    @Test
    void configIsImmutable() {
        JavaciteConfig c = ConfigLoader.load("rules:\n  pmd.CloseResource: warn");
        assertThatThrownBy(() -> c.rules().put("x", RuleLevel.OFF)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> c.bannedDeps().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(c.agentTargets()).isInstanceOf(List.class);
    }

    @Test
    void unknownTopLevelKeyNamesKeyAndValidKeys() {
        assertThatThrownBy(() -> ConfigLoader.load("tols: {}"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("'tols'")
                .hasMessageContaining("tools")
                .hasMessageContaining("spring")
                .hasMessageContaining("rules");
    }

    @Test
    void unknownToolKeyFails() {
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  pdm: on"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("'pdm'")
                .hasMessageContaining("pmd");
    }

    @Test
    void unknownNestedOptionKeyFails() {
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  jacoco: { minimum: 0.5 }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("'minimum'")
                .hasMessageContaining("min");
        assertThatThrownBy(() -> ConfigLoader.load("deps:\n  bans: []"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("'bans'")
                .hasMessageContaining("ban");
        assertThatThrownBy(() -> ConfigLoader.load("agents:\n  target: []"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("'target'");
    }

    @Test
    void toolWithoutOptionsRejectsObjectForm() {
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  pmd: { x: 1 }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("pmd");
    }

    @Test
    void removedToolsFailWithMigrationHint() {
        for (String tool : new String[] {"checkstyle", "spotbugs"}) {
            assertThatThrownBy(() -> ConfigLoader.load("tools:\n  " + tool + ": on"))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining(tool)
                    .hasMessageContaining("docs/migration.md");
        }
    }

    @Test
    void unknownRuleIdSuggestsClosestIds() {
        assertThatThrownBy(() -> ConfigLoader.load("rules:\n  pmd.LocalVariableCouldBeFina: off"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("pmd.LocalVariableCouldBeFina")
                .hasMessageContaining("pmd.LocalVariableCouldBeFinal");
    }

    @Test
    void unknownRuleWithUnknownNamespaceStillFails() {
        assertThatThrownBy(() -> ConfigLoader.load("rules:\n  nope.Thing: off"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("nope.Thing");
    }

    @Test
    void invalidValuesFail() {
        assertThatThrownBy(() -> ConfigLoader.load("rules:\n  pmd.CloseResource: loud"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("loud")
                .hasMessageContaining("error")
                .hasMessageContaining("warn")
                .hasMessageContaining("off");
        assertThatThrownBy(() -> ConfigLoader.load("spring: maybe"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("maybe");
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  sonar: maybe"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("maybe");
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  dependencyCheck: { inCheck: sometimes }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("sometimes");
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  pmd: perhaps"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("perhaps");
        assertThatThrownBy(() -> ConfigLoader.load("agents:\n  targets: [nope]"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("nope");
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  jacoco: { min: 1.5 }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("min");
        assertThatThrownBy(() -> ConfigLoader.load("java: 8"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("java");
    }

    @Test
    void versionMustBeOne() {
        assertThatThrownBy(() -> ConfigLoader.load("version: 2"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("version")
                .hasMessageContaining("1");
        assertThatThrownBy(() -> ConfigLoader.load("version: one")).isInstanceOf(ConfigException.class);
    }

    @Test
    void wrongShapesFail() {
        assertThatThrownBy(() -> ConfigLoader.load("- a\n- b")).isInstanceOf(ConfigException.class);
        assertThatThrownBy(() -> ConfigLoader.load("tools: [a]")).isInstanceOf(ConfigException.class);
        assertThatThrownBy(() -> ConfigLoader.load("rules: x")).isInstanceOf(ConfigException.class);
        assertThatThrownBy(() -> ConfigLoader.load("deps:\n  ban: x")).isInstanceOf(ConfigException.class);
    }

    @Test
    void malformedYamlFailsWithConfigException() {
        assertThatThrownBy(() -> ConfigLoader.load("tools: [unclosed")).isInstanceOf(ConfigException.class);
    }

    @Test
    void nullYamlRejected() {
        assertThatThrownBy(() -> ConfigLoader.load(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void bannedDepsMustBeGroupColonName() {
        assertThat(ConfigLoader.load("deps:\n  ban: [\"a.b:c\"]").bannedDeps()).containsExactly("a.b:c");
        for (String bad : List.of("a", "a:b:1.0", "a: b", ":b", "a:")) {
            assertThatThrownBy(() -> ConfigLoader.load("deps:\n  ban: [\"" + bad + "\"]"))
                    .as(bad)
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("exactly");
        }
    }

    @Test
    void nullOptionValueIsConfigExceptionNotNpe() {
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  spotless: { ratchetFrom: }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("ratchetFrom");
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  spotless: { ratchetFrom: 5 }"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("string");
    }

    @Test
    void nullawayRequiresErrorprone() {
        assertThatThrownBy(() -> ConfigLoader.load("tools:\n  nullaway: on\n  errorprone: off"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Error Prone");
        assertThat(ConfigLoader.load("tools:\n  errorprone: off").tool(Tool.ERRORPRONE).enabled()).isFalse();
        assertThat(ConfigLoader.load("tools:\n  nullaway: off\n  errorprone: off")).isNotNull();
    }
}
