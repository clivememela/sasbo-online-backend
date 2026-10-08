# A STAND-IN for the application's own server Dockerfile, and a temporary one.
#
# 🔴 WHY THIS EXISTS. The server repository's Dockerfile cannot build. Its
# extract stage copies `build/libs/*-boot.jar`, and the Gradle build never
# produces such a file: `bootJar` sets no classifier, so the output is
# `sasbo-online-backend-<version>.jar` alongside a `-plain.jar`. The glob
# matches nothing, `app.jar` never becomes a file, and the build dies on the
# NEXT line with `Error: Unable to access jarfile app.jar` — a message about
# extraction that says nothing about the copy that failed above it.
#
# Verified by building that repository's `build` stage alone and listing the
# directory inside it, rather than by reading either file.
#
# 🔴 AND IT IS NOT ONE DEFECT, IT IS TWO — the second only became visible once
# the first was fixed. The runtime stage copies four layer directories from
# /extract/dependencies, /extract/spring-boot-loader, /extract/snapshot-
# dependencies and /extract/application. `extract --launcher` writes all four
# under an `app/` subdirectory, so none of those paths exists either. Fixing the
# glob alone gets you a different failure, one stage later.
#
# Two independent errors, neither reachable by reading, is not a typo. It is a
# file that has never been executed.
#
# THIS FILE IS THAT DOCKERFILE WITH TWO CORRECTIONS — the jar glob and the four
# layer paths. Everything else is deliberately identical: same base images, same
# stages, same layered extraction, same non-root user, same entrypoint. It is
# duplicated rather than imported because a Dockerfile cannot include another
# one, and duplication with a stated diff is more honest than a silently
# divergent build.
#
# 🔑 DELETE THIS FILE the day the application repository's own Dockerfile
# builds. The suite is supposed to run against the artifact that actually
# deploys; right now no such artifact exists, and pretending otherwise would be
# exactly the false confidence the plans warn about. The defect is registered so
# that fixing it is a visible event rather than something nobody notices.
#
# Not applied as a fix to their file: this arc does not modify the incumbent's
# repository.

# syntax=docker/dockerfile:1
FROM gradle:9.5-jdk21 AS build
WORKDIR /home/gradle/project

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    --mount=type=cache,target=/home/gradle/project/.gradle \
    ./gradlew build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
# ⬇️ THE ONE DIFFERENCE. Theirs reads `*-boot.jar`, which matches nothing.
# `-plain.jar` is excluded by requiring the version suffix immediately before
# `.jar`, so this matches the boot jar and only the boot jar.
COPY --from=build /home/gradle/project/build/libs/*-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# ⬇️ THE SECOND DIFFERENCE, and the reason the first fix alone was not enough.
# Theirs copies from /extract/dependencies, /extract/spring-boot-loader,
# /extract/snapshot-dependencies and /extract/application. `extract --launcher`
# puts all four under an `app/` subdirectory instead, so none of those paths
# exists and the build fails with "/extract/application: not found".
COPY --from=extract --chown=app:app /extract/app/dependencies/ ./
COPY --from=extract --chown=app:app /extract/app/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /extract/app/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /extract/app/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
