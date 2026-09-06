# SASBO Online — backend

Spring Boot API for the SASBO membership administration system. PostgreSQL, Flyway migrations,
Java 21.

## Running the tests

```bash
./gradlew verify
```

That is the command. `verify` runs **both lanes** — everything `check` runs, plus the integration
tests against a real PostgreSQL — and it is the one to run before pushing.

### Two lanes, and why the split is deliberate

| Command | What runs | Needs Docker |
| --- | --- | --- |
| `./gradlew verify` | **both lanes** — the fast lane and the integration lane | **yes** |
| `./gradlew check` | the fast lane only | **no** |
| `./gradlew build` | compile, `check`, and package | **no** |

🔴 **`check` and `build` deliberately do NOT depend on the integration lane.** The everyday build
must never acquire a Docker dependency: someone compiling this project should not need a running
daemon to do it. The integration tests hang off `verify` alone, and that is the whole reason
`verify` exists as a separate task rather than being folded into `check`.

The integration lane starts a real PostgreSQL container per run via Testcontainers, applies the
Flyway migration chain to an **empty** database, and tests against the result. It never touches a
developer database.

### What it needs

- **Java 21.** The build declares a toolchain, so Gradle will fetch a matching JDK rather than
  failing on whatever happens to be on `PATH`.
- **Gradle 9.7.1**, pinned by the wrapper. Use `./gradlew`, never a locally installed `gradle`.
- **Docker**, for `verify` only — see the table above.
- **No credentials, and no `.env.local`.** That file is git-ignored, so a fresh clone has none; the
  tests must run without one and do.

### What it produces

| Path | What it is |
| --- | --- |
| `reports/junit/test/*.xml` | JUnit XML, fast lane |
| `reports/junit/integrationTest/*.xml` | JUnit XML, integration lane |
| `build/reports/tests/` | Gradle's browsable HTML report — the place to start when something is red |
| `reports/coverage/` | JaCoCo, only when asked for: `./gradlew test -Pcoverage jacocoTestReport` |

All of it is build output and all of it is git-ignored. The JUnit XML directories are **cleared at
the start of each run**: Gradle overwrites what it produces and removes nothing else, so a deleted
or renamed test would otherwise leave its last result on disk to be counted forever.

### The exit code is the whole result

`0` means every test passed. Anything else means it did not, and there is no third meaning. A run
that prints a green summary and exits non-zero has still failed.

### Some tests are disabled on purpose, and none of them is flaky

A `@Disabled` test in this project means exactly one thing: **a test written to the correct
behaviour, against a real defect, left visibly failing by design.** Fixing the defect is a
production change; this suite's job is to make it visible rather than to repair it quietly.

Every one of them cites a constant in
[`KnownDefects`](src/test/java/za/co/titandynamix/sasbo_online_backend/support/KnownDefects.java),
which carries the whole finding as its text, and
[`DisabledPopulationTest`](src/test/java/za/co/titandynamix/sasbo_online_backend/guards/DisabledPopulationTest.java)
asserts the correspondence **in both directions** — a disabled test with no constant reddens, and so
does a constant whose test has been fixed and left disabled. That second half is the one that
actually rots, because nothing else in a suite ever complains about it.

**Nothing here is skipped for being unreliable**, no test is retried, and a flaky test is a bug to
fix in the session that finds it.

> ⚠️ **The disabled reasons do not reach the JUnit XML.** `@Disabled` takes a compile-time constant
> carrying the full explanation, and Gradle's XML writer emits a bare `<skipped/>` — no message, and
> a display name that does not name the defect either. So any tool reading only the XML sees *that*
> a test was skipped and can never see *why*. Read `KnownDefects` for the reasons.

## Building and running the application

```bash
./gradlew bootRun
```

🔴 **Five environment variables are mandatory and have no defaults**: `SERVER_PORT`,
`SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD` and `SSL_MODE`. Without them the application
dies at startup with

```
Failed to bind properties under 'server.port' to java.lang.Integer: Value: "${SERVER_PORT}"
```

which reads as a type error rather than as a missing variable, and names only the first of the five.
See [`.env.example`](.env.example).

A sixth, **`FRONTEND_URL`**, has a default and is easy to miss because its absence does not stop the
application starting: unset, the security configuration trusts exactly one origin
(`http://localhost:5173`). Every other origin gets `403 Invalid CORS request` on any `POST`, `PUT`,
`PATCH` or `DELETE` — so reads work, the application looks healthy, and saving anything fails.
