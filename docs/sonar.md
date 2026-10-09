# SonarQube

javacite cooperates with SonarQube in two ways: it feeds the local tool reports into the analysis, and it can
export a quality profile that mirrors your enabled rules. It does not replace Sonar's own analysis.

## Report import

Sonar wiring is applied when `tools.sonar` is `on`, or `auto` (the default) and `SONAR_HOST_URL` or `SONAR_TOKEN` is
set in the environment. Set `sonar: off` to never wire it. Authentication stays in the environment
(`SONAR_TOKEN`); javacite never stores a token.

| Property | Gradle value | Maven value |
|---|---|---|
| `sonar.java.checkstyle.reportPaths` | `build/reports/checkstyle/main.xml` | `target/checkstyle-result.xml` |
| `sonar.java.pmd.reportPaths` | `build/reports/pmd/main.xml` | `target/pmd.xml` |
| `sonar.java.spotbugs.reportPaths` | `build/reports/spotbugs/main.xml` | `target/spotbugsXml.xml` |
| `sonar.coverage.jacoco.xmlReportPaths` | `build/reports/jacoco/test/jacocoTestReport.xml` | `target/site/jacoco/jacoco.xml` |
| `sonar.java.source` | value of `java:` | value of `java:` |

Gradle: the plugin applies `org.sonarqube` (7.5.0.8588). `./gradlew javaciteSonarProperties` prints what it set.
Maven: the extension adds `sonar-maven-plugin` 5.8.0.7211 and sets the same properties on each project.

Run the reports first, then the scanner:

```sh
export SONAR_HOST_URL=https://sonar.example.com
export SONAR_TOKEN=...                       # from your secret store, not from this repo
./gradlew check sonar                        # Gradle
mvn verify sonar:sonar                       # Maven
```

## Quality profile export

```sh
./gradlew javaciteSonarProfile                          # writes build/javacite/sonar-profile.xml
mvn io.github.wraithyy:javacite-maven-plugin:sonar-profile   # writes target/javacite/sonar-profile.xml
```

The file is a Sonar quality profile backup named `javacite` for language `java`, containing every enabled rule that
has a known sonar-java key (a `sonar:` entry in the registry, currently 31 rules). Rules at `error` export as
priority `MAJOR`, rules at `warn` as `MINOR`; rules you opted out of are left out.

Restore it into a SonarQube server with the web API:

```sh
curl -H "Authorization: Bearer $SONAR_TOKEN" -X POST \
  -F backup=@build/javacite/sonar-profile.xml \
  "$SONAR_HOST_URL/api/qualityprofiles/restore"
```

Then set the profile as default for Java or assign it to projects in the Sonar UI.

> **Status: validated on SonarQube Community Build 26.9** (`scripts/sonar-validate.sh`): the generated profile
> restores with `ruleSuccesses: 30, ruleFailures: 0`, Checkstyle findings show up as `external_checkstyle:*`
> issues and JaCoCo coverage is imported. Older server versions may differ; re-run the script against yours.

## Validating against a local SonarQube

`scripts/sonar-validate.sh` starts `sonarqube:community` in Docker, analyses `examples/gradle-spring`
with the plugin's Sonar wiring, restores the generated quality profile through
`api/qualityprofiles/restore` and prints the external issue counts and coverage reported by the
server. Run it once after changing the Sonar wiring or the profile generator:

```sh
scripts/sonar-validate.sh          # removes the container afterwards
scripts/sonar-validate.sh --keep   # leaves it running on http://localhost:9000
```

## What is not covered

- Error Prone, NullAway, ArchUnit, Spotless and the dependency audit have no Sonar rule counterpart; only
  Checkstyle, PMD and SpotBugs reports are imported (as external issues), plus JaCoCo coverage.
- Only rules with a confirmed sonar-java key are exported; the other rules are omitted rather than guessed.
- The profile does not carry rule parameters, and it does not sync back: changes in Sonar do not change
  `javacite.yml`.
- Quality gates are yours to configure in Sonar; javacite's own gate is the build (`check` / `verify`).
