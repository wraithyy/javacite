# Maven

javacite for Maven is a core extension plus a few goals. There is nothing to add to your `pom.xml`.

## Install

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

`mvn io.github.wraithyy:javacite-maven-plugin:init` (or `jbang javacite@wraithyy/javacite init`) creates or extends
this file for you. Requires Maven 3.9.x and a JVM 21 or newer running Maven; Maven 4 is not supported yet.

## How the extension works

After Maven has read all projects, the extension reads `javacite.yml` from the multi-module root and mutates the
model of every project with `jar` or `war` packaging (`pom` aggregators are left alone). For each enabled tool it
adds a plugin execution with id `javacite`; nothing is written to your `pom.xml`. An invalid `javacite.yml` fails the
build at that point.

### What is injected

| Tool | Plugin | Phase and goal | Strict settings |
|---|---|---|---|
| enforcer | `maven-enforcer-plugin` 3.6.3 | `validate`: `enforce` | `requireJavaVersion [21,)`, `bannedDependencies` from `deps.ban`, `fail=true` |
| (configs) | `javacite-maven-plugin` | `validate`: `generate-configs` | Writes `target/javacite/pmd.xml` at build time, so `mvn clean verify` does not delete it. Bound only when PMD is on. |
| compiler | `maven-compiler-plugin` | the default compile executions | `release` from `java:` always, even with Error Prone and NullAway off, unless you set `maven.compiler.release` or a plugin-level `release`. With Error Prone or NullAway on, also: `fork=true`, `-Xplugin:ErrorProne ...` with the registry levels, `error_prone_core` 2.50.0 and `nullaway` 0.14.2 in `annotationProcessorPaths`, the javac `--add-exports/--add-opens` as `-J` arguments. `jspecify` 1.0.1 is added as `provided` when you do not declare it. |
| spotless | `spotless-maven-plugin` 3.10.3 | `verify`: `check` | palantir-java-format 2.102.0, remove unused imports, trim whitespace, end with newline, `formatAnnotations` (same steps as the Gradle build) |
| pmd | `maven-pmd-plugin` 3.28.0 (PMD 7.28.0) | `verify`: `check` | generated ruleset, tests included, `failurePriority=2` |
| jacoco | `jacoco-maven-plugin` 0.8.15 | `prepare-agent`, `report`, `check` | `LINE` `COVEREDRATIO` at `tools.jacoco.min` |
| dependencyCheck | `dependency-check-maven` 13.0.0 | `verify`: `check` when `inCheck` says so | `failBuildOnCVSS`, `NVD_API_KEY` from the environment, cache in `~/.javacite/nvd`. When not bound, the plugin is still configured, so `mvn org.owasp:dependency-check-maven:check` works on demand. |

Tools set to `off` get nothing injected. Because everything hangs off `verify`, `mvn verify` is the full gate and
`mvn -DskipTests verify` the fast one (this is what the agent hooks run).

### When you already declare a plugin

- Your declaration, version and configuration are kept. javacite only adds its `javacite` execution with its own
  configuration next to yours. If an execution with id `javacite` already exists it is left alone.
- When javacite creates the plugin itself, the configuration sits on the plugin as well as on the `javacite` execution, so
  command-line goals such as `mvn spotless:apply` or `mvn pmd:check` (which run as `default-cli`) see it. When you
  declare the plugin, only the execution carries javacite's configuration: run `mvn spotless:apply@javacite` instead.
- A plugin that is only in `pluginManagement` is added with the managed version, not javacite's pinned one (the build
  logs which). Managed configuration is not applied to it.
- `maven-compiler-plugin` is the exception, because Error Prone must ride on the default compile executions. javacite
  merges into a copy of your configuration (a configuration inherited from a parent POM is never mutated): its
  `compilerArgs` are appended unless an identical argument is already there, its `annotationProcessorPaths` are
  added unless the same `artifactId` is already listed, and any other key you set wins, except `fork=false` with Error
  Prone on, which is forced to `true` with a warning.

### Forked goals caveat (PMD)

`pmd:check` first forks `pmd:pmd`, and the forked run sees only
plugin-level configuration, not the configuration of an execution. When javacite creates the plugin entry it puts
the generated ruleset at plugin level, so this works. If you declare `maven-pmd-plugin` yourself, the analysis uses your plugin-level configuration, not javacite's, and javacite logs a WARNING saying so. In that case
point it at the generated files:

```xml
<plugin>
  <artifactId>maven-pmd-plugin</artifactId>
  <configuration>
    <rulesets><ruleset>${project.build.directory}/javacite/pmd.xml</ruleset></rulesets>
  </configuration>
</plugin>
```

or, simpler, remove your declaration and let javacite own the plugin.

### JDK 21 and `.mvn/jvm.config`

The enforcer rule fails the build early when Maven runs on a JVM older than 21. Error Prone needs javac internals;
because the compiler is forked, javacite passes the required `--add-exports`/`--add-opens` as `-J` compiler
arguments, so no `.mvn/jvm.config` is needed. `examples/maven-spring/.mvn/jvm.config` shows the equivalent file if
you prefer to set it on the Maven JVM too.

### Skipping a tool

There is no javacite-specific escape hatch (no `-Dno-errorprone`). Switch tools off in `javacite.yml` so the decision
is reviewed and visible in `AGENTS.md`. The standard skip properties of the underlying plugins (for example
`-Dpmd.skip`, `-Djacoco.skip`, `-Denforcer.skip`) have not been verified
against javacite's executions; treat them as UNVERIFIED and do not rely on them in CI.

## Goals

Call them with the full coordinates, or add `io.github.wraithyy` to your `pluginGroups` for the short prefix.

| Goal | What it does |
|---|---|
| `mvn io.github.wraithyy:javacite-maven-plugin:init` | Adds the extension to `.mvn/extensions.xml`, then writes `javacite.yml`, `AGENTS.md`, editor rules, Claude Code hooks, the pre-commit hook and `ArchitectureTest`. Idempotent. See [agents.md](agents.md). |
| `mvn io.github.wraithyy:javacite-maven-plugin:doctor` | Reports JDK, config parse status, enabled tools, Spring detection and which generated files exist. |
| `mvn io.github.wraithyy:javacite-maven-plugin:sonar-profile` | Writes `target/javacite/sonar-profile.xml`, see [sonar.md](sonar.md). |
| `mvn io.github.wraithyy:javacite-maven-plugin:sonar-properties` | Writes `target/javacite/sonar-project.properties` for the official `sonar-scanner` CLI, see [sonar.md](sonar.md). |
| `mvn io.github.wraithyy:javacite-maven-plugin:check` | Alias that only reports the enabled tools. The checks themselves run in `verify`. |
| `javacite-maven-plugin:generate-configs` | Writes the generated tool configs; bound to `validate` by the extension, rarely called by hand. |
