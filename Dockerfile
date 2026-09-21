# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle wrapper and build files first to leverage layer caching.
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy the source and build the executable jar.
COPY src src
RUN ./gradlew --no-daemon bootJar

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -r -u 1001 appuser && mkdir -p /app && chown appuser:appuser /app
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
