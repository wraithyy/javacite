# SonarQube

javacite cooperates with SonarQube in two ways: it writes a scanner properties file that feeds the local tool
reports into the analysis, and it can export a quality profile that mirrors your enabled rules. It does not run the
scanner and does not replace Sonar's own analysis. The SonarQube scanner plugins for Gradle and Maven are LGPL-3.0, so
javacite does not apply them; you run the official `sonar-scanner` CLI yourself.

## Scanner properties file

```sh
./gradlew check javaciteSonarProperties                      # writes build/javacite/sonar-project.properties
mvn verify io.github.wraithyy:javacite-maven-plugin:sonar-properties   # writes target/javacite/sonar-project.properties
```

The tasks run when `tools.sonar` is `on`, or `auto` (the default) and `SONAR_HOST_URL` or `SONAR_TOKEN` is set in the
environment; `sonar: off` disables them. Run `check` / `verify` first so the report files exist. The file contains:

| Property | Content |
|---|---|
| `sonar.projectBaseDir` | absolute path of the project (host path) |
| all other paths in the project | relative to `sonar.projectBaseDir`, forward slashes; paths outside it (dependency jars in `~/.gradle` or `~/.m2`) stay absolute |
| `sonar.sources`, `sonar.tests` | main and test source directories |
| `sonar.java.binaries`, `sonar.java.test.binaries` | compiled classes |
| `sonar.java.libraries` | dependency jars |
| `sonar.java.source` | value of `java:` |
| `sonar.java.pmd.reportPaths` | PMD XML reports (imported as `external_pmd` issues) |
| `sonar.coverage.jacoco.xmlReportPaths` | JaCoCo XML report paths |

The file never contains a token. Authentication stays in the environment.

Then run the scanner CLI, installed locally or from the official image:

```sh
export SONAR_TOKEN=...                       # from your secret store, not from this repo
sonar-scanner -Dproject.settings=build/javacite/sonar-project.properties \
  -Dsonar.host.url=https://sonar.example.com -Dsonar.projectKey=my-project
```

With Docker, mount the project at its own host path and point `sonar.projectBaseDir` there. Paths in the properties
file are relative, so a different mount point such as `/usr/src` finds sources, binaries and the PMD/JaCoCo/JUnit
report files too, but the PMD XML report itself contains absolute host file names: with `/usr/src` the scanner logs
`No input file found for /Users/... No PMD issue will be imported` and no `external_pmd` issues appear. Mounting at the
host path avoids that (the path must be shared with the Docker VM on macOS):

```sh
docker run --rm -v "$PWD":"$PWD" -v "$HOME/.gradle":"$HOME/.gradle":ro -e SONAR_TOKEN sonarsource/sonar-scanner-cli \
  -Dproject.settings="$PWD"/build/javacite/sonar-project.properties -Dsonar.projectBaseDir="$PWD" \
  -Dsonar.host.url=https://sonar.example.com -Dsonar.projectKey=my-project
```

The `-v ~/.gradle` (or `~/.m2`) mount makes the absolute `sonar.java.libraries` jars visible in the container. Without
it the scanner only warns `Invalid value for 'sonar.java.libraries'` and Java analysis runs with degraded type
resolution. `scripts/sonar-validate.sh` shows a working setup for the example project.

## Quality profile export

```sh
./gradlew javaciteSonarProfile                          # writes build/javacite/sonar-profile.xml
mvn io.github.wraithyy:javacite-maven-plugin:sonar-profile   # writes target/javacite/sonar-profile.xml
```

The file is a Sonar quality profile backup named `javacite` for language `java`, containing every enabled rule that
has a known sonar-java key (a `sonar:` entry in the registry). Rules at `error` export as priority `MAJOR`, rules at
`warn` as `MINOR`; rules you opted out of are left out.

Restore it into a SonarQube server with the web API:

```sh
curl -H "Authorization: Bearer $SONAR_TOKEN" -X POST \
  -F backup=@build/javacite/sonar-profile.xml \
  "$SONAR_HOST_URL/api/qualityprofiles/restore"
```

Then set the profile as default for Java or assign it to projects in the Sonar UI.

> **Status.** The quality profile export, the properties-file flow (relative paths, Docker scanner) and the
> `external_pmd` issue import were validated on SonarQube Community Build 26.9. Re-run the script against your server
> version.

## Validating against a local SonarQube

`scripts/sonar-validate.sh` starts `sonarqube:community` in Docker, builds `examples/gradle-spring` with
`javaciteSonarProfile` and `javaciteSonarProperties`, runs the official `sonarsource/sonar-scanner-cli` container,
restores the generated quality profile through `api/qualityprofiles/restore` and prints the `external_pmd` issue
counts and coverage reported by the server. Run it once after changing the properties generator or the profile
generator:

```sh
scripts/sonar-validate.sh          # removes the container afterwards
scripts/sonar-validate.sh --keep   # leaves it running on http://localhost:9000
```

On Linux the scanner container uses `--network host`; on macOS (Docker Desktop, Rancher Desktop, colima) it reaches
the server through `host.docker.internal`.

## What is not covered

- Error Prone, NullAway, ArchUnit, Spotless and the dependency audit have no Sonar rule counterpart; only PMD reports
  are imported (as external issues), plus JaCoCo coverage.
- Security taint analysis (the SpotBugs find-sec-bugs class of findings: injection, path traversal, unsafe
  deserialization data flows) is not covered. No permissively licensed tool provides it; find-sec-bugs and Semgrep
  are LGPL. What javacite checks: PMD `security` rules (`pmd.HardCodedCryptoKey`, `pmd.InsecureCryptoIv`), Error
  Prone, and OWASP dependency-check for vulnerable dependencies. SonarQube's own analysis, if you run it, adds its
  taint rules on top.
- Only rules with a confirmed sonar-java key are exported; the other rules are omitted rather than guessed.
- The profile does not carry rule parameters, and it does not sync back: changes in Sonar do not change
  `javacite.yml`.
- Quality gates are yours to configure in Sonar; javacite's own gate is the build (`check` / `verify`).
