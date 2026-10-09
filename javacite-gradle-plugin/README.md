# javacite-gradle-plugin

Plugin id `io.github.wraithyy.javacite`. Requires the `java` plugin and a JDK 21+ build JVM
(a `java.toolchain` of 21+ also satisfies the gate). Reads `javacite.yml` from the root project.

## Tasks

| Task | What it does |
|---|---|
| `javaciteCheck` | Fast static gate, no tests, no network: `spotlessCheck`, `compileJava` + `compileTestJava` (Error Prone, NullAway), `pmdMain/Test`. Part of `check`. Tools set to `off` in `javacite.yml` are not applied and drop out of the gate. |
| `javaciteFix` | Applies automatic fixes (`spotlessApply`). `-PspotlessIdeHook=<abs path>` formats just that file. |
| `javaciteInit` | Writes `javacite.yml` (if absent), `AGENTS.md` block, `CLAUDE.md` symlink, editor rule files, `.javacite/hook-*.sh`, `.claude/settings.json` hooks, `.githooks/pre-commit` (+ `core.hooksPath`), and `src/test/java/<base>/ArchitectureTest.java`. Idempotent. |
| `javaciteDoctor` | Reports JDK gate, config parse status, enabled tools, Spring detection, generated files present or missing, hooksPath, and whether `javacite-archunit` is on the test classpath. |
| `javaciteAudit` | OWASP dependency-check (`dependencyCheckAnalyze`). Joins `check` when `tools.dependencyCheck.inCheck` is `always`, or `ci` and the `CI` environment variable is set. Never runs offline. |
| `javaciteSonarProfile` | Writes `build/javacite/sonar-profile.xml` (Sonar quality profile backup format) from the resolved rules. |
| `javaciteSonarProperties` | Writes `build/javacite/sonar-project.properties` (project key, sources, tests, classes, classpaths, PMD, JaCoCo and JUnit report paths) for a standalone `sonar-scanner`. Always registered on the root project unless `tools.sonar` is `off`; runs no scanner. |
| `generateJavaciteConfigs` | Writes `build/javacite/{pmd.xml,spring.detected}` from `javacite.yml`; the tool tasks depend on it. |

Tool versions are pinned in `gradle/libs.versions.toml` and compiled into `JavaciteVersions` by the
`generateJavaciteVersions` task.

## Generated files (`javaciteInit`)

`javacite.yml`, `AGENTS.md`, `CLAUDE.md` (symlink), `.cursor/rules/javacite.mdc`,
`.github/copilot-instructions.md`, `.windsurf/rules/javacite.md`, `.javacite/hook-format.sh`,
`.javacite/hook-check.sh`, `.claude/settings.json` (merged), `.githooks/pre-commit`,
`src/test/java/<base>/ArchitectureTest.java`. Shared markdown files get a
`<!-- javacite:start -->` / `<!-- javacite:end -->` block; user content outside it is kept.

## Sonar

`javaciteSonarProperties` writes `build/javacite/sonar-project.properties`; no Sonar Gradle plugin is applied.
Run the scanner yourself: `sonar-scanner -Dproject.settings=build/javacite/sonar-project.properties`.
`tools.sonar: auto` and `on` both always write the file (it is cheap); `off` skips the task.

## Dependency-check

- Set `NVD_API_KEY` in the environment; without it the plugin logs a warning and NVD updates are slow and rate limited.
- The NVD database is cached in `~/.javacite/nvd`, shared across projects.
- `tools.dependencyCheck.failOnCvss` maps to `failBuildOnCVSS`; reports are written as XML and HTML.

## Notes on tool semantics

- **PMD `warn`**: Gradle's `Pmd` task has no severity gate, so any reported violation fails the build.
  A PMD rule set to `warn` is rendered at priority 3 and reported, but it still fails `pmdMain`;
  `rulesMinimumPriority` is set to 5 so no rule is dropped. Use `off` to silence a PMD rule.
- **Spring detection** reads `org.springframework.boot:spring-boot` from the resolved `compileClasspath`
  at execution time; `spring: on|off` overrides it.
- The removed LGPL-licensed tool keys under `tools` fail validation with a migration message.

## Plugin conventions

- All task inputs are `Provider`/`Property` values; tasks never touch `Project` in actions.
- `javacite.yml` is read through `providers.fileContents`, so the configuration cache
  tracks it and editing the file invalidates the cache.
- `javacite.yml` is always `<rootProject>/javacite.yml` (no override). It is read eagerly when the plugin is
  applied, because tool wiring branches on it; a parse error fails with `javacite.yml: <message>`.
- `javaciteInit`, `javaciteDoctor`, `javaciteSonarProfile` and `javaciteSonarProperties` exist on the root project only; Spring
  detection there is "any Java project has `spring-boot` on its `compileClasspath`".
- Tool versions are pinned in `gradle/libs.versions.toml` and generated into `JavaciteVersions`.
- Third-party plugins are applied programmatically and only when their tool is not `off`.
- `afterEvaluate` is used only for the JDK 21 gate (run immediately if the project is already evaluated), never to compute task inputs.
