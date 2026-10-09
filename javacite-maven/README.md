# javacite-maven-plugin

Maven build extension plus goals for javacite. Coordinates:
`io.github.wraithyy:javacite-maven-plugin:0.1.0-SNAPSHOT`.

## Bootstrap

```
mvn io.github.wraithyy:javacite-maven-plugin:init
```

Writes `javacite.yml`, agent rule files, hooks and `.mvn/extensions.xml` (merged into an existing file). From then
on the extension is active and the prefix-less goals below need the full coordinates (or a `pluginGroups` entry
for `io.github.wraithyy` in `settings.xml`).

## Goals

| Goal | Purpose |
|---|---|
| `init` | Aggregator. Runs the shared `InitWriter` (reports created/updated/unchanged), creates or merges `.mvn/extensions.xml`, activates `core.hooksPath` only when `.git` sits in the project root. Idempotent. |
| `doctor` | Prints JDK gate (21+), Maven version, config path and parse status, tools on/off, Spring detection, generated files present/missing, `core.hooksPath`, whether `javacite-archunit` is declared. Fails on an invalid `javacite.yml`. |
| `sonar-profile` | Writes `target/javacite/sonar-profile.xml` (SonarQube profile backup of the enabled rules). |
| `generate-configs` | Writes `target/javacite/{checkstyle,pmd,spotbugs-exclude}.xml`. Bound to `validate` by the extension. |
| `check` | Reports enabled tools; the checks themselves are lifecycle executions, use `mvn -q -DskipTests verify`. |

## How the extension injects executions

`JavaciteLifecycleParticipant.afterProjectsRead` reads `javacite.yml` from the multi-module root and, for every
`jar`/`war` module, adds an execution with id `javacite` per enabled tool (enforcer, generate-configs, Error Prone and
NullAway on the compiler, spotless, checkstyle, pmd, spotbugs, jacoco, dependency-check, sonar). A plugin the user
already declares keeps its own configuration and only gains the extra execution. Generated tool configs are written
at build time into `target/javacite/`, so `mvn clean verify` does not delete them.

### PMD and SpotBugs caveat

`pmd:check` and `spotbugs:check` fork their analysis goal (`pmd:pmd`, `spotbugs:spotbugs`) and the forked execution
only sees plugin-level configuration. For plugins the extension creates it therefore puts the configuration at plugin
level. If the user declares `maven-pmd-plugin` or `spotbugs-maven-plugin` themselves, their configuration wins for the
forked goal and the javacite ruleset/filter is applied only to the `javacite` execution; set the ruleset there too.

## Integration tests

`src/it/` holds `maven-invoker-plugin` projects, run in `integration-test` after `invoker:install`:

| IT | Asserts |
|---|---|
| `clean-spring` | copy of `examples/maven-spring`, `verify` succeeds |
| `seed-nullaway` | `verify` fails with `[NullAway]` |
| `seed-checkstyle` | `verify` fails in checkstyle (`HideUtilityClassConstructor`) |
| `pmd-off` | `tools.pmd: off` leaves checkstyle in the build but no `pmd` execution |
| `init-goal` | two `init` runs; files exist, second run reports only `unchanged` |

`@project.version@` in `.mvn/extensions.xml` is filtered by invoker. The plugin is installed into the regular local
repository (no `localRepositoryPath`), so Spring and the tool plugins come from the shared `~/.m2` cache instead of a
fresh `target/local-repo` download; the first run needs network access. Runtime is roughly 30 s with a warm cache.

```
./gradlew mavenBuild                      # publishes javacite-core, then mvn install (ITs included)
mvn -B -f javacite-maven/pom.xml verify   # ITs only need the core in mavenLocal
mvn -B -f javacite-maven/pom.xml install -DskipITs
mvn -B -f javacite-maven/pom.xml verify -Dinvoker.test=pmd-off   # one IT
```

Do not run two builds of this module concurrently: they share `target/it` and clobber each other's logs.
