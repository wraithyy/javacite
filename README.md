# javacite

[![CI](https://img.shields.io/badge/CI-placeholder-lightgrey)](https://github.com/wraithyy/javacite/actions)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-placeholder-lightgrey)](https://central.sonatype.com/namespace/io.github.wraithyy)
[![Gradle Plugin Portal](https://img.shields.io/badge/Plugin%20Portal-placeholder-lightgrey)](https://plugins.gradle.org/plugin/io.github.wraithyy.javacite)

Ultracite for Java. One `javacite.yml`, one line in your build, and Spotless, Error Prone, NullAway, Checkstyle,
PMD, SpotBugs, ArchUnit, JaCoCo and OWASP dependency-check run with every rule at error level. Opt out per rule or
per tool when a rule does not fit. `init` generates `AGENTS.md`, editor rule files, Claude Code hooks and a git
pre-commit hook from the same rule set, so coding agents are told exactly what the build will enforce.
Inspired by [Ultracite](https://www.ultracite.ai); not affiliated with it.

## Quick start

Requirements: build JVM 21 or newer (Error Prone needs it), compile target 17 or newer (`java:` in `javacite.yml`,
applied as `--release`).

### Gradle

```kotlin
// build.gradle.kts
plugins {
    java
    id("io.github.wraithyy.javacite") version "0.1.0"
}
```

```sh
./gradlew javaciteInit   # javacite.yml, AGENTS.md, hooks, ArchitectureTest; safe to re-run
./gradlew check          # full gate
```

### Maven

```xml
<!-- .mvn/extensions.xml -->
<extensions>
  <extension>
    <groupId>io.github.wraithyy</groupId>
    <artifactId>javacite-maven-plugin</artifactId>
    <version>0.1.0</version>
  </extension>
</extensions>
```

```sh
mvn io.github.wraithyy:javacite-maven-plugin:init   # .mvn/extensions.xml, javacite.yml, AGENTS.md, hooks
mvn verify                                          # full gate
```

### jbang (no build edits needed first)

```sh
jbang javacite@wraithyy/javacite/v0.1.0 init     # detects Gradle or Maven, wires the build, writes everything
jbang javacite@wraithyy/javacite/v0.1.0 doctor
```

Pin the release tag as above (recommended): the `jbang-catalog.json` inside that tag carries that release's
`javacite-cli` version. Without a tag (`javacite@wraithyy/javacite`) JBang reads the catalog from `main`, which may point
at a SNAPSHOT used for development.

## What you get

| Concern | Tool | Version |
|---|---|---|
| Formatting | Spotless + palantir-java-format | 8.10.3 (Gradle) / 3.10.3 (Maven) + 2.102.0 |
| Bug patterns | Error Prone | 2.50.0 |
| Null safety | NullAway + JSpecify | 0.14.2 + 1.0.1 |
| Style | Checkstyle | 14.3.0 |
| Code smells | PMD | 7.28.0 |
| Defects | SpotBugs | 4.10.4 |
| Architecture | ArchUnit (`javacite-archunit`) | 1.5.1 |
| Coverage gate | JaCoCo (line coverage, default 0.80) | 0.8.15 |
| Vulnerable dependencies | OWASP dependency-check | 13.0.0 |
| Banned dependencies, JDK gate | Gradle resolution check / maven-enforcer | 3.6.3 (Maven) |
| Quality server | SonarQube report import and profile export | scanner 7.5.0.8588 (Gradle) / 5.8.0.7211 (Maven) |

230 rules in total, listed in [docs/rules.md](docs/rules.md). A Spring Boot project additionally gets layering and
injection rules ([docs/spring-preset.md](docs/spring-preset.md)).

## Minimal `javacite.yml`

```yaml
version: 1
java: 17
tools:
  dependencyCheck: off          # run it in a dedicated CI job instead
  jacoco: { min: 0.70 }
rules:
  # Records of DTOs with many components trip this; revisit when the module is split.
  checkstyle.ParameterNumber: off
  archunit.spring.NoMockBeanInUnitTests: warn
```

Everything not listed keeps its strict default. A missing file means all defaults. Full reference:
[docs/configuration.md](docs/configuration.md).

## How it works

```
rules-registry.yml  +  javacite.yml
          |
          v   (ResolvedRules: registry defaults, overridden per rule and per tool)
  generators ----> build/javacite/{checkstyle.xml, pmd.xml, spotbugs-exclude.xml}
          |------> Error Prone / NullAway compiler arguments
          |------> ArchUnit rule switches
          |------> sonar-profile.xml
          '------> AGENTS.md, CLAUDE.md, Cursor / Copilot / Windsurf rules, hook scripts
```

The Gradle plugin and the Maven core extension read the same `javacite.yml`, generate the tool configs at build time
and wire each third-party tool with its strict settings. A rule or tool you opt out of disappears from every
generated output, including `AGENTS.md`, so agents are never told to follow a rule the build does not check.

## Documentation

- [Configuration reference](docs/configuration.md)
- [Rule reference](docs/rules.md) (generated)
- [Spring preset](docs/spring-preset.md)
- [Agents: AGENTS.md, Claude Code hooks, pre-commit](docs/agents.md)
- [SonarQube](docs/sonar.md)
- [Maven specifics](docs/maven.md)
- [Adopting on an existing codebase](docs/migration.md)
- [Gradle plugin tasks](javacite-gradle-plugin/README.md)
- Examples: [`examples/gradle-spring`](examples/gradle-spring), [`examples/maven-spring`](examples/maven-spring)

## License

Apache-2.0, see [LICENSE](LICENSE).
