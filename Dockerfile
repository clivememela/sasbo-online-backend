# Build stage: uses the official Gradle image matching the project's wrapper version
FROM gradle:9.5-jdk21 AS build
WORKDIR /home/gradle/project

# Copy only dependency-related files first to leverage Docker layer caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
# Download all dependencies; this layer is cached until build.gradle changes
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy source code and build the JAR, using only cached dependencies (no network)
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# Runtime stage: minimal Alpine-based JRE image to keep the final image small
FROM eclipse-temurin:21-jre-alpine
# Create a non-root user before setting up the working directory
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
# Copy only the built JAR from the build stage and assign ownership to non-root user
COPY --from=build --chown=app:app /home/gradle/project/build/libs/*.jar app.jar
# Switch to non-root user to reduce attack surface
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
