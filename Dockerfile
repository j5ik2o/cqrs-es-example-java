# FROM --platform=linux/amd64 eclipse-temurin:17-jdk AS build
ARG TARGETARCH
FROM --platform=${BUILDPLATFORM} gradle AS build
WORKDIR /workspace/app

COPY . /workspace/app
RUN --mount=type=cache,target=/root/.gradle ./gradlew clean :bootstrap:build --no-daemon
RUN mkdir -p bootstrap/build/dependency && (cd bootstrap/build/dependency; jar -xf ../libs/*-SNAPSHOT.jar)

FROM --platform=${BUILDPLATFORM} eclipse-temurin:17.0.10_7-jdk

VOLUME /tmp
ARG DEPENDENCY=/workspace/app/bootstrap/build/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
WORKDIR /app

COPY boot.sh /app

ENTRYPOINT ["./boot.sh"]