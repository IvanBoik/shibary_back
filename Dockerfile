# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# Cache dependencies
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
