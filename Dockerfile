# Use the official Gradle image for the build stage. The image includes Gradle and a matching JDK.
FROM gradle:8.13-jdk21 AS build

# Use the gradle user's project directory for better defaults and permissions
WORKDIR /home/gradle/project

# Copy only the files needed to resolve dependencies first so Docker layer cache can be used
# (adjust filenames if your project uses different names/locations)
COPY gradle/ gradle/
COPY gradlew .
COPY settings.gradle settings.gradle
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
COPY build.gradle build.gradle

# Ensure the wrapper is executable
RUN chmod +x ./gradlew

# Download dependencies to leverage build cache. The command is allowed to fail
# in case the project has additional configuration; it's a best-effort cache warm-up.
RUN ./gradlew --no-daemon dependencies || true

# Copy the rest of the source and run the build (skip tests to keep image build fast)
COPY . .
RUN ./gradlew build -x test --no-daemon

# Final runtime image: use a small official JDK runtime image
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Create and use a non-root user for runtime security
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app --shell /usr/sbin/nologin app

# Copy the built jar from the build stage
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
