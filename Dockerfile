# syntax=docker/dockerfile:1

# --- Build stage ---
# Use the official Gradle image: the Gradle distribution is already baked in, so the build
# never downloads gradle-*-bin.zip from services.gradle.org (the source of the CI timeout).
FROM gradle:9.3.1-jdk21 AS build

WORKDIR /app

# Persist resolved dependencies between builds via the BuildKit cache mount.
ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY settings.gradle.kts build.gradle.kts ./

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
