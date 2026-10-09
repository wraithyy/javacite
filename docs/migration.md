# Adopting javacite on an existing codebase

javacite is strict by default, which is what you want on new code and a wall on a legacy codebase. Adopt it in
steps: make the build green on day one with opt-outs, then remove them as you fix things.

## 1. Baseline

Run `init` on a branch and see what you are dealing with:

```sh
./gradlew javaciteInit && ./gradlew check --continue     # Maven: mvn io.github.wraithyy:javacite-maven-plugin:init && mvn verify
```

## 2. Switch noisy tools off, then back on one at a time

```yaml
version: 1
tools:
  # Re-enable in this order; each is its own pull request.
  spotless: on
  errorprone: on
  nullaway: { mode: onlyNullMarked }   # checks only @NullMarked code, so nothing fails yet
  checkstyle: off
  pmd: off
  spotbugs: off
  archunit: off
  dependencyCheck: off
```

`onlyNullMarked` is the gentle way into null safety: annotate one package at a time with `@NullMarked`
(`package-info.java`). Alternatively `{ mode: annotatedPackages, packages: [com.acme.billing] }` checks listed
package trees only.

## 3. Format without drowning the diff

Do not reformat a whole repository in a feature branch. Either ratchet:

```yaml
tools:
  spotless: { ratchetFrom: origin/main }   # only files changed since main are checked (Gradle)
```

or format everything once in a dedicated commit (`./gradlew javaciteFix`, `mvn spotless:apply`) and list that commit in
`.git-blame-ignore-revs`. Maven does not support `ratchetFrom` yet, so use the second route there.

## 4. Opt out rules, with a reason

Prefer switching off one rule over a whole tool. Every opt-out gets a comment, because the file is the record of your
exceptions and a reviewer reads it:

```yaml
rules:
  # Legacy DTOs with 9+ constructor parameters; remove when the DTOs become records (JIRA-123).
  checkstyle.ParameterNumber: off
  # Magic numbers dominate the pricing module; fix per package, then drop this.
  checkstyle.MagicNumber: off
  pmd.AvoidDuplicateLiterals: off
```

Opted-out rules vanish from `AGENTS.md`, so agents will not enforce them either. Mind `warn`: it does not relax
Checkstyle or PMD on Gradle ([configuration.md](configuration.md#rules)); use `off` there.

## 5. Suppress individual violations

Use suppressions for genuine one-off exceptions, not as a migration tool. The generated `AGENTS.md` tells agents not
to suppress rules to make a check pass, so suppressions are a human decision made in review.

| Tool | Suppression |
|---|---|
| Checkstyle | `@SuppressWarnings("checkstyle:MagicNumber")`, or `// CHECKSTYLE:OFF` / `// CHECKSTYLE:ON` around a block |
| PMD | `@SuppressWarnings("PMD.AvoidDuplicateLiterals")`, or `// NOPMD` on the line |
| SpotBugs | `@SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "...")` (needs `com.github.spotbugs:spotbugs-annotations` as `compileOnly`) |
| Error Prone | `@SuppressWarnings("UnusedVariable")` with the check name |
| NullAway | `@SuppressWarnings("NullAway")` |
| ArchUnit | No annotation. Turn the rule `off` or `warn` in `javacite.yml` |

## 6. Ramp the coverage gate

Start at what you have, then ratchet up. Measure first (`./gradlew test jacocoTestReport`), then set the gate just
below the current line coverage:

```yaml
tools:
  # 0.52 measured at adoption. Raise by 0.05 per quarter until 0.80.
  jacoco: { min: 0.50 }
```

Raising `min` in the same pull request that adds tests keeps the gate honest.

## 7. Turn on the rest

Enable `checkstyle`, `pmd`, `spotbugs` and `archunit` one at a time, opting out the handful of rules that fire in
hundreds of places, and add `dependencyCheck` last (`inCheck: ci`, with `NVD_API_KEY` in CI). When `tools:` is
all `on` and `rules:` holds only justified exceptions, you have arrived.
