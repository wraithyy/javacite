# Configuration reference

javacite reads `javacite.yml` from the root of the project (Gradle: the root project directory; Maven: the
multi-module root). The file is optional: if it is missing or empty, every default below applies. Any unknown key
fails the build, so typos never pass silently.

```yaml
version: 1
java: 17
tools:
  spotless: on
  errorprone: on
  nullaway: { mode: onlyNullMarked }
  pmd: on
  archunit: on
  jacoco: { min: 0.80 }
  dependencyCheck: { failOnCvss: 7, inCheck: ci }
  enforcer: on
  sonar: auto
spring: auto
rules: {}
deps:
  ban: ["commons-lang:commons-lang", "log4j:log4j"]
agents:
  targets: [agents-md, claude-md, cursor, copilot, windsurf, claude-hooks, git-hooks]
```

This is the full default configuration. The `init` template writes only `version`, `tools` (all `on`, `sonar: auto`),
`spring: auto` and `rules: {}`.

## Top-level keys

| Key | Values | Default | Meaning |
|---|---|---|---|
| `version` | `1` | `1` | Schema version. Anything else is rejected. |
| `java` | integer >= 17 | `17` | Compile target, applied as `--release` (`options.release` / `maven.compiler.release`). The build JVM must still be 21+. |
| `tools` | mapping | all on | Per-tool switches and options, see below. |
| `spring` | `auto`, `on`, `off` | `auto` | Spring preset. `auto` = Spring Boot detected on the project. |
| `rules` | mapping of rule id to `error`, `warn`, `off` | `{}` | Per-rule level overrides. |
| `deps.ban` | list of `group:artifact` | `commons-lang:commons-lang`, `log4j:log4j` | Banned dependencies. A list you provide replaces the default list. |
| `agents.targets` | list, see below | all seven | Which agent files `init` generates. |

YAML booleans are accepted wherever `on` or `off` is: `true` is `on`, `false` is `off`.

## `tools`

Every tool is on unless you say otherwise. A tool set to `off` is not applied to the build at all, its rules are
forced off, and it vanishes from `AGENTS.md`.

| Key | Values | Notes |
|---|---|---|
| `spotless` | `on`, `off`, `{ ratchetFrom: <git ref> }` | Format with palantir-java-format, remove unused imports, trim whitespace. |
| `errorprone` | `on`, `off` | Error Prone checks, compiled into `javac`. On Gradle, `off` also disables NullAway because it runs as an Error Prone plugin. |
| `nullaway` | `on`, `off`, `{ mode, packages }` | See below. |
| `pmd` | `on`, `off` | |
| `archunit` | `on`, `off` | Controls whether `init` writes `ArchitectureTest` and whether the registry's ArchUnit rules are active. |
| `jacoco` | `on`, `off`, `{ min }` | Line coverage gate. |
| `dependencyCheck` | `on`, `off`, `{ failOnCvss, inCheck }` | OWASP dependency-check. |
| `enforcer` | `on`, `off` | Maven only: `maven-enforcer-plugin` with the JDK 21 gate and `deps.ban`. On Gradle this key has no effect; the JDK gate and `deps.ban` are always applied (use `deps.ban: []` to disable the latter). |
| `sonar` | `auto`, `on`, `off` | `auto` enables the Sonar tasks when `SONAR_HOST_URL` or `SONAR_TOKEN` is set in the environment. See [sonar.md](sonar.md). |

Tools without an option object (`errorprone`, `pmd`, `archunit`, `enforcer`) reject
`{ ... }`. An object means "on with these options"; to switch a tool off use the plain `off` value.

### `nullaway`

```yaml
tools:
  nullaway: { mode: onlyNullMarked }                              # default
  # nullaway: { mode: annotatedPackages, packages: [com.acme] }
```

| Option | Values | Default |
|---|---|---|
| `mode` | `onlyNullMarked`, `annotatedPackages` | `onlyNullMarked` |
| `packages` | list of package prefixes | empty; required and non-empty when `mode: annotatedPackages` |

`onlyNullMarked` checks only code inside `@NullMarked` scopes (JSpecify), which is the right choice when you adopt
null checking package by package. `annotatedPackages` treats every class under the listed packages as checked.

### `jacoco`

```yaml
tools:
  jacoco: { min: 0.80 }
```

`min` is the minimum line coverage as a ratio between 0 and 1, default `0.80`. `jacoco: on` means the default.

### `dependencyCheck`

```yaml
tools:
  dependencyCheck: { failOnCvss: 7, inCheck: ci }
```

| Option | Values | Default | Meaning |
|---|---|---|---|
| `failOnCvss` | number 0 to 10 | `7.0` | Fail when a vulnerability scores at or above this CVSS. |
| `inCheck` | `ci`, `always`, `never` | `ci` | When the audit joins `check` / `verify`. `ci` = only when the `CI` environment variable is set. |

Set `NVD_API_KEY` in the environment; without it NVD updates are slow and rate limited. The NVD cache lives in
`~/.javacite/nvd`. On Gradle you can always run the audit on demand with `./gradlew javaciteAudit`.

### `spotless`

```yaml
tools:
  spotless: { ratchetFrom: origin/main }
```

`ratchetFrom` is a git reference; only files changed relative to it are checked. Useful when adopting javacite on
existing code, see [migration.md](migration.md). Currently honoured by the Gradle plugin only.

## `rules`

```yaml
rules:
  pmd.LocalVariableCouldBeFinal: off
  archunit.spring.NoFieldInjection: warn
```

Keys are rule ids from [rules.md](rules.md) (`<tool>.<Name>`); values are `error`, `warn` or `off`. An unknown id
fails the build and lists the five closest valid ids. Rules of a tool that is `off` are always off, and the
`archunit.spring.*` rules are off when the Spring preset is not active.

What `warn` means depends on the tool, because the tools themselves differ:

| Tool | `warn` behaviour |
|---|---|
| Error Prone, NullAway | Reported as a compiler warning (`-Xep:Name:WARN`); the build does not fail. |
| PMD | Priority 3 (errors are priority 2). Maven fails only on priority 2, so `warn` is reported only. Gradle's `Pmd` task has no severity gate: any violation fails the build, so `warn` is reported but does not relax anything. Use `off` to silence a PMD rule on Gradle. |
| ArchUnit | Rules are either evaluated or skipped. `warn` is evaluated, so the test still fails; only `off` skips a rule. |
| Sonar profile | `error` exports as priority `MAJOR`, `warn` as `MINOR`. |
| `AGENTS.md` | The rule is listed with a `(warning)` suffix. |

When you need a rule to stop failing the build, use `off`, and leave a YAML comment saying why.

## `deps.ban`

```yaml
deps:
  ban: ["commons-lang:commons-lang", "log4j:log4j", "org.apache.commons:commons-collections4"]
```

Entries are `group:artifact`, without a version. Gradle fails dependency resolution of any configuration that
requests a banned coordinate (the tools' own classpaths are exempt). Maven uses `maven-enforcer-plugin`
`bannedDependencies` (needs `tools.enforcer` on). Your list replaces the default, so repeat the defaults if you want
to keep them. `ban: []` disables the check.

## `agents.targets`

| Target | Generates |
|---|---|
| `agents-md` | `AGENTS.md` |
| `claude-md` | `CLAUDE.md` as a symlink to `AGENTS.md` (also creates `AGENTS.md`) |
| `cursor` | `.cursor/rules/javacite.mdc` |
| `copilot` | `.github/copilot-instructions.md` |
| `windsurf` | `.windsurf/rules/javacite.md` |
| `claude-hooks` | `.javacite/hook-*.sh` and the hook entries in `.claude/settings.json` |
| `git-hooks` | `.githooks/pre-commit` and `git config core.hooksPath .githooks` |

Details in [agents.md](agents.md).

## Validation errors

Configuration errors fail the build (or `init`) with a message naming the path. Examples:

```
Unknown key 'tool' at '<root>'. Valid keys: [version, java, tools, spring, rules, deps, agents]
Unknown key 'pdm' at 'tools'. Valid keys: [spotless, errorprone, nullaway, pmd, archunit, jacoco, dependencyCheck, enforcer, sonar]
Invalid value 'maybe' at 'tools.pmd'. Valid values: [on, off]
Tool 'pmd' does not accept options (at 'tools.pmd'); use on or off
Value of 'java' must be 17 or higher but was 11
Mode 'annotatedPackages' at 'tools.nullaway' requires a non-empty 'packages' list
Value of 'tools.jacoco.min' must be between 0 and 1 but was 80
Unknown rule id 'pmd.FinalLocal'. Closest valid ids: [pmd.LocalVariableCouldBeFinal, ...]
Unknown agent target 'vim' at 'agents.targets'. Valid targets: [agents-md, claude-md, ...]
Unsupported 'version': 2. Only version 1 is supported
```

A file that is not valid YAML fails with `Invalid YAML: ...`.

### Removed tools: `checkstyle` and `spotbugs`

javacite no longer ships Checkstyle or SpotBugs (both LGPL-2.1). Setting `tools.checkstyle` or `tools.spotbugs`, to
any value, and any `checkstyle.*` or `spotbugs.*` rule id, fails validation with a message pointing to the migration
guide. This is deliberate: silently ignoring the key would leave you believing a check still runs. Remove the keys
(and rule overrides) or follow [migration.md](migration.md#migrating-from-checkstyle-and-spotbugs) to map them to
their PMD, Error Prone and ArchUnit replacements.
