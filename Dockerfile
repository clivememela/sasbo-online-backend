# syntax=docker/dockerfile:1
# Build stage: uses the official Gradle image matching the project's wrapper version
FROM gradle:9.5-jdk21 AS build
WORKDIR /home/gradle/project

# Copy only dependency-related files first to leverage Docker layer caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy source code and config early to let BuildKit manage compile caches
COPY config ./config
COPY src ./src

# OPTIMIZATION: Use BuildKit cache mounts for the Gradle user home and project build cache.
# This prevents downloading dependencies on every single build.
RUN --mount=type=cache,target=/home/gradle/.gradle \
    --mount=type=cache,target=/home/gradle/project/.gradle \
    ./gradlew build -x test --no-daemon

# Extract stage: unpack JAR into layers
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
# The cache mount doesn't persist to the final image, but the output JAR is saved here
COPY --from=build /home/gradle/project/build/libs/*-boot.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=extract --chown=app:app /extract/dependencies/ ./
COPY --from=extract --chown=app:app /extract/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /extract/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /extract/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
