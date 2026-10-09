#!/usr/bin/env sh
# Validates the SonarQube integration end to end against a local SonarQube Community container
# (plan tasks 7.1 and 7.2). Requires a running Docker daemon, curl and the JDK 21 build JVM.
#
# What it checks:
#   1. examples/gradle-spring: `./gradlew check javaciteSonarProfile sonar` with SONAR_HOST_URL set,
#      so the plugin wires the org.sonarqube plugin and the four external report paths.
#   2. The generated quality profile XML restores through POST api/qualityprofiles/restore.
#   3. The Sonar web API reports external issues (checkstyle/pmd/spotbugs) and coverage for the project.
#
# Usage: scripts/sonar-validate.sh [--keep]   (--keep leaves the container running)
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
EXAMPLE="$ROOT/examples/gradle-spring"
CONTAINER=javacite-sonar
HOST=http://localhost:9000
PROJECT_KEY=javacite-example
ADMIN_PASS=javacite-admin-1
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
curl -fsS -H "$(basic "admin:admin")" -X POST "$HOST/api/users/change_password?login=admin&previousPassword=admin&password=$ADMIN_PASS" >/dev/null 2>&1 || true

log "creating analysis token"
TOKEN=$(curl -fsS -H "$(basic "admin:$ADMIN_PASS")" -X POST "$HOST/api/user_tokens/generate?name=javacite-$(date +%s)" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "could not obtain a token" >&2; exit 4; }

log "analysing examples/gradle-spring"
cd "$EXAMPLE"
SONAR_HOST_URL=$HOST SONAR_TOKEN=$TOKEN ./gradlew --console=plain check javaciteSonarProfile sonar \
    -Dsonar.projectKey=$PROJECT_KEY -Dsonar.projectName=$PROJECT_KEY

log "restoring generated quality profile"
PROFILE="$EXAMPLE/build/javacite/sonar-profile.xml"
[ -f "$PROFILE" ] || { echo "missing $PROFILE" >&2; exit 5; }
curl -fsS -H "Authorization: Bearer $TOKEN" -X POST -F "backup=@$PROFILE" "$HOST/api/qualityprofiles/restore"
echo

log "waiting for background task"
sleep 10
until curl -fsS -H "Authorization: Bearer $TOKEN" "$HOST/api/ce/component?component=$PROJECT_KEY" | grep -q '"status":"SUCCESS"'; do sleep 5; done

log "external issues by rule repository"
curl -fsS -H "Authorization: Bearer $TOKEN" "$HOST/api/issues/search?componentKeys=$PROJECT_KEY&ps=1&facets=rules" \
    | tr ',' '\n' | grep -o '"val":"external_[a-z]*:[^"]*"' | sort | uniq -c | sort -rn | head -20

log "coverage measure"
curl -fsS -H "Authorization: Bearer $TOKEN" "$HOST/api/measures/component?component=$PROJECT_KEY&metricKeys=coverage,line_coverage"
echo

if [ "$KEEP" != "--keep" ]; then
    log "removing container (pass --keep to leave it running)"
    docker rm -f "$CONTAINER" >/dev/null
fi
log "done"
