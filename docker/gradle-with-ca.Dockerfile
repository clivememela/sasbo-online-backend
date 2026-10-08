# A Gradle build image that trusts an extra CA — for machines whose outbound
# HTTPS is intercepted and re-signed by a corporate proxy or endpoint-security
# product.
#
# WHY THIS EXISTS AT ALL
# The server image is built from the application repository's own Dockerfile,
# which is correct and must stay that way: it is the artifact that actually
# deploys, and this repository does not get to modify it. But its build stage
# runs `./gradlew build` inside `gradle:9.5-jdk21`, and a JVM reads its OWN
# trust store rather than the operating system's. On an intercepted machine
# every dependency download fails with:
#
#   PKIX path building failed: unable to find valid certification path
#
# which names certificates and reads like the Gradle repositories being down.
# It is the same interception that already broke npm inside the Playwright
# container — a third toolchain reached by a third route.
#
# 🔑 HOW IT IS APPLIED WITHOUT TOUCHING THE APPLICATION REPOSITORY
# BuildKit can substitute a named base image at build time, so their Dockerfile
# runs verbatim while resolving `gradle:9.5-jdk21` to this patched one:
#
#   docker build -f docker/gradle-with-ca.Dockerfile \
#     --build-arg CA_FILE=proxy-root.crt -t sasbo-gradle-ca:local <dir holding the cert>
#
#   docker build --build-context gradle:9.5-jdk21=docker-image://sasbo-gradle-ca:local \
#     -t sasbo-online-backend:local <the server repository>
#
# Nothing in the server repository changes, and on a machine without
# interception neither command is needed — build the server image directly.

FROM gradle:9.5-jdk21

ARG CA_FILE=proxy-root.crt
COPY ${CA_FILE} /tmp/extra-ca.crt

# ── A SECOND problem, which only appears once the certificate one is fixed ────
#
# The application's wrapper pins Gradle 9.7.1 while this base image ships 9.5.1,
# so `./gradlew` downloads its own distribution — 164 MB — on every build. The
# wrapper's `networkTimeout` is 10 s, which is not enough for that download on a
# connection where every byte is being inspected, and it fails partway with a
# read error rather than anything that mentions a timeout.
#
# Both of those live in the application repository (`gradle-wrapper.properties`)
# and are not this arc's to change. So the distribution is baked in instead,
# pre-populated at the exact path the wrapper looks for it.
#
# 🔑 GRADLE_USER_HOME IS MOVED, and that is the part that makes it work.
# Their Dockerfile mounts a BuildKit cache at `/home/gradle/.gradle` — the
# default Gradle home — and a cache mount MASKS whatever the image has at that
# path. Anything baked into the default location would be invisible. Pointing
# GRADLE_USER_HOME somewhere the mount does not cover is what lets the baked
# distribution survive.
#
# ⚠️ The cost, stated: dependency downloads no longer land in that cache mount
# either, so they are not reused between builds. Correctness over speed — a
# build that is slower on a cold cache beats one that cannot finish.
ARG GRADLE_DIST=wrapper-dists
COPY ${GRADLE_DIST} /opt/gradle-home/wrapper/dists
ENV GRADLE_USER_HOME=/opt/gradle-home

# Into the JDK's own trust store, which is what Gradle actually consults.
# `-noprompt` because the build is non-interactive; `changeit` is the JDK's
# documented default store password, not a secret.
USER root
RUN keytool -importcert \
      -keystore "$JAVA_HOME/lib/security/cacerts" \
      -storepass changeit \
      -noprompt \
      -alias extra-proxy-root \
      -file /tmp/extra-ca.crt \
 && rm /tmp/extra-ca.crt
