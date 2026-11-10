#!/usr/bin/env bash

set -eu

# shellcheck disable=SC2046
cd $(dirname "$0") || exit

export ARCH=$(uname -m)
echo "ARCH=${ARCH}"
export TAG_PREFIX="aarch64"

if [ "$ARCH" = "x86_64" ]; then
  TAG_PREFIX="x86_64"
  ARCH="amd64"
fi

if [ "$ARCH" = "aarch64" ]; then
  TAG_PREFIX="aarch64"
  ARCH="arm64"
fi

F_OPTION="-f ../docker-compose/docker-compose-applications.yml -f ../docker-compose/docker-compose-e2e-test.yml"
RUN_E2E=1
COMPOSE_SERVICES="localstack mysql refinery dynamodb-setup write-api-server-1 read-model-updater-1 read-api-server-1"

while getopts d OPT; do
  # shellcheck disable=SC2220
  case ${OPT} in
  "d")
    F_OPTION=""
    RUN_E2E=0
    COMPOSE_SERVICES="localstack mysql refinery dynamodb-setup"
    ;;
  esac
done

# Remove processed options from $@
shift $(($OPTIND - 1))

if [ "${RUN_E2E}" -eq 1 ]; then
  APP_IMAGE="cqrs-es-example-java:latest-${ARCH}"
  if ! docker image inspect "${APP_IMAGE}" >/dev/null 2>&1; then
    echo "Error: ${APP_IMAGE} is not built. Run ./tools/scripts/docker-build.sh first." >&2
    exit 1
  fi
  echo "Building the e2e test image"
  (cd ../e2e-test && make build)
fi

collect_logs_on_failure() {
  local status=$?
  trap - EXIT
  if [ "${status}" -ne 0 ]; then
    echo "e2e failed with exit code ${status}; collecting compose logs" >&2
    # shellcheck disable=SC2086
    docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} logs --no-color --tail=200 || true
  fi
  exit "${status}"
}
trap collect_logs_on_failure EXIT

# shellcheck disable=SC2086
docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} down -v --remove-orphans
# shellcheck disable=SC2086
docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} up --remove-orphans --force-recreate --renew-anon-volumes -d ${COMPOSE_SERVICES} "$@"

if [ "${RUN_E2E}" -eq 0 ]; then
  echo "Started databases only (-d); skipping the end-to-end verification"
  exit 0
fi

# shellcheck disable=SC2086
for container in $(docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} ps -aq); do
  published=$(docker port "${container}")
  if [ -n "${published}" ]; then
    echo "Error: $(docker inspect --format '{{.Name}}' "${container}") publishes a host port in the e2e stack: ${published}" >&2
    exit 1
  fi
done

echo "Running the end-to-end verification"
# shellcheck disable=SC2086
docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} run --rm --no-deps e2e-test

# The read model updater replays the retained head records on restart.
# read_model_positions must drop them, so no duplicate insert reaches MySQL.
# shellcheck disable=SC2086
rmu_duplicates_before=$(docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} logs --no-color read-model-updater-1 | grep -cE "Duplicate entry|SQLIntegrityConstraintViolationException" || true)
echo "Restarting the read model updater to verify head stream replay"
# shellcheck disable=SC2086
docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} restart read-model-updater-1
# shellcheck disable=SC2086
docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} run --rm --no-deps e2e-test
# shellcheck disable=SC2086
rmu_duplicates_after=$(docker compose -p cqrs-es-example-java -f ../docker-compose/docker-compose-databases.yml ${F_OPTION} logs --no-color read-model-updater-1 | grep -cE "Duplicate entry|SQLIntegrityConstraintViolationException" || true)

if [ "${rmu_duplicates_after}" -gt "${rmu_duplicates_before}" ]; then
  echo "Error: the read model updater applied a duplicate after the restart (${rmu_duplicates_before} -> ${rmu_duplicates_after})" >&2
  exit 1
fi

echo "End-to-end verification and restart replay succeeded"
