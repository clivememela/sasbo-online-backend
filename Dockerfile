# Build stage (v2): uses the official Gradle image matching the project's wrapper version
FROM gradle:9.5-jdk21 AS build
# Use the gradle user's project directory for better defaults and permissions
WORKDIR /home/gradle/project

# Copy only dependency-related files first to leverage Docker layer caching
# Copy only the files needed to resolve dependencies first so Docker layer cache can be used
# (adjust filenames if your project uses different names/locations)
COPY gradle/ gradle/
COPY gradlew .
COPY settings.gradle settings.gradle
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
COPY build.gradle build.gradle

# Ensure the wrapper is executable,
# Download all dependencies; this layer is cached until build.gradle changes
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy source code and build the JAR, using only cached dependencies (no network)
COPY config ./config
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /home/gradle/project/build/libs/sasbo-online-backend-0.0.1-SNAPSHOT.jar /app.jar
RUN chown app:app /app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]


