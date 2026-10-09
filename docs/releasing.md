# Releasing javacite

Artifacts and where they go:

| Artifact | Built by | Published to |
|---|---|---|
| `javacite-core`, `javacite-archunit`, `javacite-cli` | Gradle (`javacite.publishing` convention, vanniktech plugin) | Maven Central |
| `javacite-maven-plugin` | Maven (`javacite-maven/`, profile `release`) | Maven Central |
| `io.github.wraithyy.javacite` Gradle plugin | Gradle (`com.gradle.plugin-publish`) | Gradle Plugin Portal |
| `javacite-cli-<version>.jar` | Gradle | GitHub release asset, also used by `jbang javacite` |

The version lives in `gradle.properties` (`version=0.1.0-SNAPSHOT`) and is overridden in CI with `-Pversion=<x.y.z>`.
The `-Pjavacite.version` property no longer exists.

## Secrets (GitHub repository secrets)

| Secret | Purpose | How to get it |
|---|---|---|
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | Central Portal user token (not your login) | central.sonatype.com > account > "Generate User Token" |
| `SIGNING_KEY` | ASCII-armored private GPG key | `gpg --armor --export-secret-keys <KEYID>` (publish the public key with `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`) |
| `SIGNING_PASSWORD` | Passphrase of that key | chosen when creating the key |
| `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET` | Plugin Portal API key and secret | plugins.gradle.org > sign in > API Keys |
| `NVD_API_KEY` (optional) | Speeds up dependency-check in CI | nvd.nist.gov/developers/request-an-api-key |

Never paste these into chat, code or logs. Locally, use environment variables
(`ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_signingInMemoryKey`, `GRADLE_PUBLISH_KEY`, ...) or your password manager.

## One-time setup

1. Central Portal: sign in at central.sonatype.com with the GitHub account `wraithyy`. A `io.github.wraithyy` namespace is
   registered automatically for GitHub sign-ins; if not, add it under Namespaces and verify it by creating the temporary
   public repository the Portal asks for. Wait until the namespace shows "Verified".
2. Plugin Portal: sign in at plugins.gradle.org with GitHub, create the API key. The plugin id prefix
   `io.github.wraithyy` is accepted for GitHub accounts of that name; the first publish of a new plugin id is reviewed by
   the Portal and may take a day or two to appear.
3. Create the GPG key, publish the public key to a keyserver, add the secrets above.
4. Create the GitHub repository `wraithyy/javacite` and push.

## First-release checklist

1. All of 1-4 above done; `main` is green in CI.
   The publish job runs in the GitHub environment `release`; add required reviewers there to get an approval gate.
2. Dry run: Actions > Release > Run workflow with `dry_run` checked. Expect a green run that only builds and runs
   `publishToMavenLocal`.
3. Locally confirm the Plugin Portal validation with real keys:
   `GRADLE_PUBLISH_KEY=... GRADLE_PUBLISH_SECRET=... ./gradlew -Pversion=0.1.0 publishPlugins --validate-only`
   (the plugin refuses `-SNAPSHOT` versions and needs valid keys even for validation).
4. Set `jbang-catalog.json` to the release version in the release commit (`io.github.wraithyy:javacite-cli:0.1.0`); the
   workflow fails a tag whose catalog does not match (see below).
5. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`.
6. The workflow uploads the Gradle modules and the Maven plugin as pending deployments, publishes the Gradle plugin,
   and creates the GitHub release with the CLI jar.
7. In the Central Portal (Deployments), inspect each deployment and click Publish. Artifacts appear on Maven Central after
   10 to 30 minutes. For later releases use `publishAndReleaseToMavenCentral` and `-Dcentral.autoPublish=true` to skip the click.
8. Verify: `jbang io.github.wraithyy:javacite-cli:0.1.0 --help`, `jbang javacite@wraithyy/javacite/v0.1.0 doctor` and a fresh Gradle project applying
   `id("io.github.wraithyy.javacite") version "0.1.0"`.

Central releases are immutable. If something is wrong, fix forward with the next patch version.

## jbang-catalog.json

`jbang-catalog.json` points at `io.github.wraithyy:javacite-cli:<version>`. The recommended way to use it is tag-pinned:
`jbang javacite@wraithyy/javacite/v0.1.0 init`. JBang reads the catalog from that tag, so the commit that gets tagged
must already carry the release's GAV; the release workflow checks this and fails the run otherwise (nothing is published
before that check). The catalog on `main` may keep the `0.1.0-SNAPSHOT` GAV for development, and its `description` says so.
`main` keeps `version=0.1.0-SNAPSHOT` in `gradle.properties`; after a release, bump it and the catalog back to the next
SNAPSHOT by hand.

## Signing

Signing is skipped when no key is configured, so `./gradlew publishToMavenLocal` works locally. The release workflow passes
`-Pjavacite.release=true`, which makes a missing key a build failure instead of an unsigned upload.
For the Maven plugin the `maven-gpg-plugin` `bc` signer reads `MAVEN_GPG_KEY` and `MAVEN_GPG_PASSPHRASE`.
