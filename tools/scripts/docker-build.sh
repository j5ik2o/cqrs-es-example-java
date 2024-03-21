#!/usr/bin/env bash

set -eu

cd $(dirname "$0") || exit

if [ "$#" -gt 0 ]; then
    ARCH=$1
else
    ARCH=$(uname -m)
fi

echo "ARCH=${ARCH}"

APP_NAME=cqrs-es-example-java
LOCAL_REPO_NAME=${APP_NAME}

TAG=latest
LOCAL_URI=${LOCAL_REPO_NAME}:${TAG}
LOCAL_AMD64_URI=${LOCAL_REPO_NAME}:${TAG}-amd64
LOCAL_ARM64_URI=${LOCAL_REPO_NAME}:${TAG}-arm64

pushd ../../

IS_ALL=0

if [ "$ARCH" == "all" ]; then
    IS_ALL=1
fi

PIDS=()

if [ "$ARCH" == "arm64" ] || [ "$ARCH" == "aarch64" ] || [ "$IS_ALL" -eq 1 ]; then

docker buildx build --builder amd-arm --platform linux/arm/v7 \
  --build-context eclipse-temurin:17-jdk=docker-image://arm32v7/eclipse-temurin:17-jdk \
  -t $LOCAL_ARM64_URI --load -f Dockerfile . &

fi

if [ "$ARCH" == "x86_64" ] || [ "$IS_ALL" -eq 1 ]; then

docker buildx build --builder amd-arm --platform linux/amd64 \
  --build-context eclipse-temurin:17-jdk=docker-image://eclipse-temurin:17-jdk \
  -t $LOCAL_AMD64_URI --load -f Dockerfile . &
PIDS+=($!)

fi

for PID in "${PIDS[@]}"; do
    wait "$PID"
done

popd