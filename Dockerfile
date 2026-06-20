# syntax=docker/dockerfile:1

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Keep the Gradle distribution (wrapper/dists) and dependency caches in GRADLE_USER_HOME
# so the BuildKit cache mount below can persist them across builds.
ENV GRADLE_USER_HOME=/root/.gradle

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# Normalize line endings (in case gradlew was committed with CRLF) and cache dependencies.
# The cache mount preserves the downloaded Gradle distribution and resolved dependencies
# between builds, avoiding re-downloading gradle-*-bin.zip on every run.
RUN --mount=type=cache,target=/root/.gradle \
    sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test


# --- Runtime stage ---
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
