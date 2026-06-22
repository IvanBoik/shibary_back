# syntax=docker/dockerfile:1

# Single multi-module build used for both images (main app and SBA server). The image to produce is
# selected via build args:
#   - shibary-back  : GRADLE_TASK=:bootJar           JAR_DIR=build/libs
#   - shibary-admin : GRADLE_TASK=:sba-server:bootJar JAR_DIR=sba-server/build/libs

# --- Build stage ---
# Use the official Gradle image: the Gradle distribution is already baked in, so the build
# never downloads gradle-*-bin.zip from services.gradle.org (the source of the CI timeout).
FROM gradle:9.3.1-jdk21 AS build

ARG GRADLE_TASK=:bootJar
ARG JAR_DIR=build/libs

WORKDIR /app

# Persist resolved dependencies between builds via the BuildKit cache mount.
ENV GRADLE_USER_HOME=/home/gradle/.gradle

# Copy build scripts first so dependency resolution is cached independently of source changes.
COPY settings.gradle.kts build.gradle.kts ./
COPY sba-server/build.gradle.kts ./sba-server/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon

COPY src ./src
COPY sba-server/src ./sba-server/src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle ${GRADLE_TASK} --no-daemon -x test

# Normalize the produced artifact path for the runtime stage.
RUN cp ${JAR_DIR}/*.jar /app/app.jar

# --- Runtime stage ---
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
