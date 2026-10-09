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
  pmd: off
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
  pmd.ExcessiveParameterList: off
  # Generated mappers in the pricing module; fix per package, then drop this.
  pmd.UnnecessaryBoxing: off
  pmd.AvoidDuplicateLiterals: off
```

Opted-out rules vanish from `AGENTS.md`, so agents will not enforce them either. Mind `warn`: it does not relax
PMD on Gradle ([configuration.md](configuration.md#rules)); use `off` there.

## 5. Suppress individual violations

Use suppressions for genuine one-off exceptions, not as a migration tool. The generated `AGENTS.md` tells agents not
to suppress rules to make a check pass, so suppressions are a human decision made in review.

| Tool | Suppression |
|---|---|
| PMD | `@SuppressWarnings("PMD.AvoidDuplicateLiterals")`, or `// NOPMD` on the line |
| Error Prone | `@SuppressWarnings("UnusedVariable")` with the check name (the Error Prone name, without the `errorprone.` prefix) |
| NullAway | `@SuppressWarnings("NullAway")` |
| ArchUnit | No annotation. Turn the rule `off` in `javacite.yml` (`warn` still fails the test) |

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

Enable `pmd` and `archunit` one at a time, opting out the handful of rules that fire in
hundreds of places, and add `dependencyCheck` last (`inCheck: ci`, with `NVD_API_KEY` in CI). When `tools:` is
all `on` and `rules:` holds only justified exceptions, you have arrived.

## Migrating from Checkstyle and SpotBugs

Pre-release javacite builds (before 0.1.0) ran Checkstyle and SpotBugs. Both are LGPL-2.1, so javacite dropped them
and moved their rules to PMD, Error Prone and ArchUnit. A `javacite.yml` that still contains `tools.checkstyle`,
`tools.spotbugs` or any `checkstyle.*` / `spotbugs.*` rule id fails validation with a pointer to this section.

Steps: delete the two tool keys, replace each `checkstyle.X: off|warn` override with the id from the table below
(or drop it if the rule is in the dropped list), run `init` again so `AGENTS.md` and the hooks are regenerated, then
run the full check once.

### Checkstyle id to replacement

"Partial" means the replacement is close but not identical (different thresholds, narrower scope); expect some new
findings and some old ones to disappear. Verify each against [rules.md](rules.md).

| Checkstyle id | Replacement | Match |
|---|---|---|
| `FinalLocalVariable` | `pmd.LocalVariableCouldBeFinal` | exact |
| `FinalParameters` | `pmd.MethodArgumentCouldBeFinal` | exact |
| `HiddenField` | `errorprone.HidingField` | partial: only fields hiding superclass fields, not locals or parameters shadowing fields |
| `IllegalCatch` | `pmd.AvoidCatchingGenericException`, `pmd.AvoidCatchingThrowable` | exact |
| `IllegalThrows` | `pmd.AvoidThrowingRawExceptionTypes`, `errorprone.ThrowSpecificExceptions` | exact |
| `MultipleVariableDeclarations` | `pmd.OneDeclarationPerLine` | exact |
| `ParameterAssignment` | `pmd.AvoidReassigningParameters` | exact |
| `SimplifyBooleanExpression` | `pmd.SimplifyBooleanExpressions` | exact |
| `SimplifyBooleanReturn` | `pmd.SimplifyBooleanReturns` | exact |
| `StringLiteralEquality` | `pmd.UseEqualsToCompareStrings` | exact |
| `EqualsHashCode` | `errorprone.EqualsHashCode`, `pmd.OverrideBothEqualsAndHashcode` | exact |
| `EqualsAvoidNull` | `pmd.LiteralsFirstInComparisons` | exact |
| `CovariantEquals` | `pmd.SuspiciousEqualsMethodName` | partial |
| `DefaultComesLast` | `pmd.DefaultLabelNotLastInSwitch` | exact |
| `EmptyStatement` | `pmd.UnnecessarySemicolon` | partial |
| `FallThrough` | `errorprone.FallThrough`, `pmd.ImplicitSwitchFallThrough` | exact |
| `InnerAssignment` | `pmd.AssignmentInOperand` | exact |
| `MissingSwitchDefault` | `pmd.NonExhaustiveSwitch` | exact |
| `ModifiedControlVariable` | `pmd.AvoidReassigningLoopVariables` | exact |
| `NestedIfDepth` | `pmd.AvoidDeeplyNestedIfStmts` | partial: PMD default depth differs |
| `NoClone` | `archunit.NoCloneOverride` | exact |
| `NoFinalizer` | `errorprone.Finalize`, `pmd.AvoidCallingFinalize` | exact |
| `UnnecessaryParentheses` | `errorprone.UnnecessaryParentheses`, `pmd.UselessParentheses` | exact |
| `AvoidDoubleBraceInitialization` | `errorprone.DoubleBraceInitialization`, `pmd.DoubleBraceInitialization` | exact |
| `NeedBraces` | `pmd.ControlStatementBraces` | exact |
| `EmptyBlock` | `pmd.EmptyControlStatement` | partial |
| `EmptyCatchBlock` | `pmd.EmptyCatchBlock`, `errorprone.EmptyCatch` | exact |
| `AvoidNestedBlocks` | `pmd.UnnecessaryBlock` | exact |
| `FinalClass` | `pmd.ClassWithOnlyPrivateConstructorsShouldBeFinal` | exact |
| `HideUtilityClassConstructor` | `pmd.InstantiableUtilityClass` | partial |
| `InnerTypeLast` | `pmd.FieldDeclarationsShouldBeAtStartOfClass` | partial: fields only |
| `MutableException` | `archunit.ImmutableExceptions` | exact |
| `OneTopLevelClass` | `errorprone.MultipleTopLevelClasses` | exact |
| `VisibilityModifier` | `archunit.FieldsMustBePrivate` | exact |
| `AvoidStarImport` | `errorprone.WildcardImport` | exact |
| `IllegalImport` | `pmd.DontImportSun` | exact |
| `RedundantImport` | `pmd.UnnecessaryImport` | exact |
| `UnusedImports` | `pmd.UnnecessaryImport` (Spotless also removes unused imports) | exact |
| `MissingJavadocMethod`, `MissingJavadocType` | `pmd.CommentRequired` | partial: one rule for both |
| `JavadocMethod` | `errorprone.InvalidParam`, `errorprone.InvalidThrows` | partial |
| `JavadocStyle` | `errorprone.InvalidBlockTag`, `errorprone.MissingSummary` | partial: HTML well-formedness not checked |
| `InvalidJavadocPosition` | `pmd.DanglingJavadoc` | exact |
| `NonEmptyAtclauseDescription` | `errorprone.EmptyBlockTag` | exact |
| `SummaryJavadoc` | `errorprone.MissingSummary` | partial |
| `ModifierOrder` | `pmd.ModifierOrder` | exact |
| `RedundantModifier` | `pmd.UnnecessaryModifier` | exact |
| `ConstantName`, `MemberName`, `StaticVariableName` | `pmd.FieldNamingConventions` | exact |
| `LocalFinalVariableName`, `LocalVariableName` | `pmd.LocalVariableNamingConventions` | exact |
| `MethodName` | `pmd.MethodNamingConventions` | exact |
| `ParameterName` | `pmd.FormalParameterNamingConventions` | exact |
| `TypeName` | `pmd.ClassNamingConventions` | exact |
| `MethodLength` | `pmd.NcssCount` | partial: counts statements, not lines |
| `ParameterNumber` | `pmd.ExcessiveParameterList` | partial: different default limit |
| `UpperEll` | `pmd.LongLiteralEndingWithLowercaseL` | exact |

The exact/partial split above is a best-effort classification; the authoritative description of each rule is in
[rules.md](rules.md) and in the PMD and Error Prone documentation.

### Dropped without replacement

- `MagicNumber`, `NestedForDepth`, `ThrowsCount`, `PackageDeclaration`, `ArrayTypeStyle`: no permissively licensed
  equivalent. Review them by hand.
- `OneStatementPerLine`: the formatter (palantir-java-format) enforces it.
- `OuterTypeFilename`: `javac` rejects it.

### SpotBugs

SpotBugs bug categories overlap heavily with Error Prone and PMD, which javacite already runs. The one real gap is
security taint analysis (find-sec-bugs): no permissively licensed tool does it. What remains is PMD's `security`
rules (`pmd.HardCodedCryptoKey`, `pmd.InsecureCryptoIv`), Error Prone and OWASP dependency-check. If you need taint
analysis, run a dedicated SAST tool in CI; see [sonar.md](sonar.md#what-is-not-covered).

### Keeping Checkstyle or SpotBugs yourself

Nothing stops you from running them next to javacite; javacite just no longer configures or reports on them. Apply
the tools through your own build, outside `javacite.yml`.

Gradle:

```kotlin
plugins {
    java
    id("io.github.wraithyy.javacite") version "0.1.0"
    checkstyle                                              // built-in Gradle plugin
    id("com.github.spotbugs") version "<current version>"   // community plugin; the spotbugs engine itself is LGPL
}
checkstyle { toolVersion = "<current version>" }
```

Maven: declare `maven-checkstyle-plugin` / `spotbugs-maven-plugin` in your `pom.xml`.

You own their configuration, license review and upgrades. If your organisation bans LGPL, this is exactly what you
cannot do. The LGPL-2.1 obligations apply to you as the user of those tools, not to javacite.

### Suppression equivalents

| Before | Now |
|---|---|
| `@SuppressWarnings("checkstyle:Name")`, `// CHECKSTYLE:OFF` | `@SuppressWarnings("PMD.RuleName")` or `// NOPMD` for a PMD replacement; `@SuppressWarnings("CheckName")` for an Error Prone replacement |
| `@SuppressFBWarnings` | `@SuppressWarnings("PMD.RuleName")` or `@SuppressWarnings("CheckName")`, whichever tool now owns the rule |
| `checkstyle.X: off` in `javacite.yml` | the replacement id `off` in `javacite.yml` |
| (new rules) `archunit.FieldsMustBePrivate`, `archunit.NoCloneOverride`, `archunit.ImmutableExceptions` | no annotation; set the rule `off` in `javacite.yml` |
