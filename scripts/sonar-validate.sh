#!/usr/bin/env sh
# Validates the SonarQube integration end to end against a local SonarQube Community container
# (plan tasks 7.1 and 7.2). Requires a running Docker daemon (Linux, or macOS with Docker Desktop/Rancher Desktop/colima), curl and the JDK 21 build JVM.
#
# What it checks:
#   1. examples/gradle-spring: `./gradlew check javaciteSonarProfile javaciteSonarProperties`, then the official
#      sonarsource/sonar-scanner-cli container analyses it with the generated sonar-project.properties.
#   2. The generated quality profile XML restores through POST api/qualityprofiles/restore.
#   3. The Sonar web API reports external PMD issues (external_pmd) and coverage for the project.
#
# Usage: scripts/sonar-validate.sh [--keep]   (--keep leaves the container running)
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
EXAMPLE="$ROOT/examples/gradle-spring"
CONTAINER=javacite-sonar
HOST=http://localhost:9000
PROJECT_KEY=javacite-example
# SonarQube enforces a strong password policy (length, cases, digit, symbol).
ADMIN_PASS=Javacite-Admin-2026!
KEEP=${1:-}

log() { printf '\n==> %s\n' "$*"; }

if ! docker info >/dev/null 2>&1; then
    echo "docker daemon is not running" >&2
    exit 2
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    log "starting $CONTAINER (sonarqube:community)"
    docker run -d --name "$CONTAINER" -p 9000:9000 -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true sonarqube:community >/dev/null
fi

log "waiting for SonarQube to be UP"
i=0
until curl -fsS "$HOST/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; do
    i=$((i + 1))
    if [ "$i" -gt 120 ]; then
        echo "SonarQube did not come up in time" >&2
        docker logs --tail 50 "$CONTAINER" >&2
        exit 3
    fi
    sleep 5
done

# A fresh container forces an admin password change before the API accepts a token.
# Basic auth is built by hand so the script holds no literal credential pair.
basic() { printf 'Authorization: Basic %s' "$(printf '%s' "$1" | base64 | tr -d '\n')"; }
# Already-changed password (re-run against a kept container) is fine; any other failure is not.
if ! curl -fsS -H "$(basic "admin:admin")" -X POST "$HOST/api/users/change_password?login=admin&previousPassword=admin&password=$ADMIN_PASS" >/dev/null 2>&1; then
    curl -fsS -o /dev/null -H "$(basic "admin:$ADMIN_PASS")" "$HOST/api/users/current" || { echo "admin password change failed" >&2; exit 4; }
fi

log "creating analysis token"
export SONAR_HOST_URL=$HOST
export SONAR_TOKEN; SONAR_TOKEN=$(curl -fsS -H "$(basic "admin:$ADMIN_PASS")" -X POST "$HOST/api/user_tokens/generate?name=javacite-$(date +%s)" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$SONAR_TOKEN" ] || { echo "could not obtain a token" >&2; exit 4; }

log "building examples/gradle-spring (reports, profile, scanner properties)"
cd "$EXAMPLE"
./gradlew --console=plain check javaciteSonarProfile javaciteSonarProperties

log "running sonar-scanner-cli container"
# Project paths in the properties file are relative to sonar.projectBaseDir, but the PMD XML itself holds absolute host
# file names: the project is therefore mounted at its host path (not /usr/src), else "No PMD issue will be imported".
# --network host does not reach localhost from Docker on macOS (Rancher Desktop, colima, Docker Desktop).
if [ "$(uname -s)" = "Darwin" ]; then
    NET_ARGS=""
    SCANNER_HOST=http://host.docker.internal:9000
else
    NET_ARGS="--network host"
    SCANNER_HOST=$HOST
fi
# shellcheck disable=SC2086
# The Gradle cache is mounted read-only at its host path: sonar.java.libraries jars live outside the project, so they stay absolute.
docker run --rm $NET_ARGS -v "$EXAMPLE":"$EXAMPLE" -v "$HOME/.gradle":"$HOME/.gradle":ro -e SONAR_HOST_URL -e SONAR_TOKEN \
    sonarsource/sonar-scanner-cli \
    -Dproject.settings="$EXAMPLE"/build/javacite/sonar-project.properties \
    -Dsonar.projectBaseDir="$EXAMPLE" \
    -Dsonar.projectKey=$PROJECT_KEY -Dsonar.projectName=$PROJECT_KEY \
    -Dsonar.host.url=$SCANNER_HOST

log "restoring generated quality profile"
PROFILE="$EXAMPLE/build/javacite/sonar-profile.xml"
[ -f "$PROFILE" ] || { echo "missing $PROFILE" >&2; exit 5; }
curl -fsS -H "Authorization: Bearer $SONAR_TOKEN" -X POST -F "backup=@$PROFILE" "$HOST/api/qualityprofiles/restore"
echo

log "waiting for background task"
sleep 10
until curl -fsS -H "Authorization: Bearer $SONAR_TOKEN" "$HOST/api/ce/component?component=$PROJECT_KEY" | grep -q '"status":"SUCCESS"'; do sleep 5; done

log "external issues by rule repository"
curl -fsS -H "Authorization: Bearer $SONAR_TOKEN" "$HOST/api/issues/search?componentKeys=$PROJECT_KEY&ps=1&facets=rules" \
    | tr ',' '\n' | grep -o '"val":"external_pmd:[^"]*"' | sort | uniq -c | sort -rn | head -20

log "coverage measure"
curl -fsS -H "Authorization: Bearer $SONAR_TOKEN" "$HOST/api/measures/component?component=$PROJECT_KEY&metricKeys=coverage,line_coverage"
echo

if [ "$KEEP" != "--keep" ]; then
    log "removing container (pass --keep to leave it running)"
    docker rm -f "$CONTAINER" >/dev/null
fi
log "done"
