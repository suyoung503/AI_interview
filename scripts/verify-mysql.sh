#!/usr/bin/env bash
set +x
set -euo pipefail
cd "$(dirname "$0")/.."
if (( $# > 1 )) || [[ ${1:-} != "" && ${1:-} != "-o" ]]; then
    echo "Usage: scripts/verify-mysql.sh [-o]" >&2
    exit 2
fi

# Only ever start and remove a container owned by this invocation.
umask 077
scratch=$(mktemp -d)
container=
cleanup() {
    local status=$?
    rm -rf "$scratch"
    if [[ -n "$container" ]] && ! docker rm -fv "$container" >/dev/null; then
        echo "Could not remove the disposable MySQL container: $container" >&2
        exit 1
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

database=ai_interview_verify_$(openssl rand -hex 8)
username=verifier
password=$(openssl rand -hex 24)
printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_ROOT_HOST=localhost\nMYSQL_DATABASE=%s\nMYSQL_USER=%s\nMYSQL_PASSWORD=%s\n' \
    "$(openssl rand -hex 24)" "$database" "$username" "$password" > "$scratch/mysql.env"
container=$(docker run -d -p 127.0.0.1::3306 --env-file "$scratch/mysql.env" mysql:8.0)
binding=$(docker port "$container" 3306/tcp)
port=${binding##*:}
for ((attempt = 0; attempt < 90; attempt++)); do
    if docker exec "$container" mysqladmin --protocol=TCP ping --silent >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
if ((attempt == 90)); then
    echo "Disposable MySQL did not become ready within 90 seconds." >&2
    exit 1
fi

export MYSQL_VERIFICATION_URL="jdbc:mysql://127.0.0.1:$port/$database"
export MYSQL_VERIFICATION_USERNAME="$username" MYSQL_VERIFICATION_PASSWORD="$password"
env -u GROQ_API_KEY -u INTERVIEW_ENV_FILE ./mvnw "$@" \
    -Dspring.profiles.active=test \
    '-Dspring.config.location=classpath:application.properties,classpath:application-test.yml' \
    -Dgroq.live.confirmed=false -Dgroq.followup.diagnostic.confirmed=false \
    -Dgroq.technical.probe.confirmed=false -Dtest=MySqlIntegrationTest test
