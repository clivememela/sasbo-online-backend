# Build stage: uses the official Gradle image matching the project's wrapper version
FROM gradle:9.5-jdk21 AS build
WORKDIR /home/gradle/project

# Copy only dependency-related files first to leverage Docker layer caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
# Download all dependencies; this layer is cached until build.gradle changes
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy source code and build the JAR, using only cached dependencies (no network)
COPY config ./config
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# Extract stage: unpack JAR into layers so dependencies and app code are separate Docker layers
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

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
