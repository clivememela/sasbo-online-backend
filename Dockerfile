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

# The LOCAL default only. The runtime port is `server.port` (`${SERVER_PORT:8080}`), and a host that
# chooses its own must set SERVER_PORT to match: Render health-checks $PORT, which defaults to 10000,
# and Spring Boot reads SERVER_PORT rather than PORT -- so `sasbo-api` sets SERVER_PORT=10000. This line
# is metadata and binds nothing; it is kept at the local value so `docker run -p 8080:8080` still works.
EXPOSE 8080
# Force the JVM to respect container memory limits and use a max of 75% of available RAM
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]



