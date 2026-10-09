# gradle-spring example

A small Spring Boot 3.5 web app (controller, service, repository) that applies the
`io.github.wraithyy.javacite` Gradle plugin. It shows what a project looks like after
`javaciteInit` and with `./gradlew check` green.

## What it demonstrates

- One plugin line wires Spotless, Error Prone + NullAway, Checkstyle, PMD, SpotBugs, JaCoCo (min 0.8
  line coverage) and ArchUnit; `javacite.yml` holds the defaults (nothing is opted out).
- ArchUnit rules from `javacite-archunit` run in `ArchitectureTest` (no field injection, no package
  cycles, Spring layering).
- Generated agent files (`AGENTS.md`, `CLAUDE.md` symlink, Cursor/Copilot/Windsurf rules), Claude Code
  hooks (`.claude/settings.json`, `.javacite/*.sh`) and a git pre-commit hook (`.githooks/pre-commit`).
- The plugin and `javacite-archunit` come from the repo root through `includeBuild("../..")`.

## Commands

```sh
export JAVA_HOME=<JDK 21>
./gradlew javaciteInit     # idempotent bootstrap of config, agent files, hooks, ArchitectureTest
./gradlew javaciteDoctor   # diagnose the setup
./gradlew javaciteFix      # spotlessApply
./gradlew check            # full gate; add --configuration-cache for fast re-runs
./gradlew javaciteAudit    # OWASP dependency-check (set NVD_API_KEY)
```

Enable the pre-commit hook in your own repo with `git config core.hooksPath .githooks`; init skips this
here because the example is not a git root.
