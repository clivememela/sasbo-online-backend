# SASBO Online Backend

REST API backend for the **SASBO** membership management system (South African Buried Services Officers).
Handles member records, bank and branch information, union representatives, subscriptions, recruitment
channels, and the administrative reference data that supports member capture and maintenance.

The API is consumed by a companion single-page frontend — [`sasbo-online`](https://github.com/Fazlu101/sasbo-online)
(React + Vite) — and runs on a developer machine with PostgreSQL and Gradle. Since 2026-09-22 the project is
local only: it left the incumbent developer's Render and Vercel, and nothing deploys `main`.

---

## Table of contents

- [Overview](#overview)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Database](#database)
- [Running the application](#running-the-application)
- [API reference](#api-reference)
- [Data seeding](#data-seeding)
- [Testing](#testing)
- [Error handling](#error-handling)
- [Security and CORS](#security-and-cors)
- [Frontend integration](#frontend-integration)
- [Deployment](#deployment)
- [Contributing](#contributing)

---

## Overview

The backend is a layered Spring Boot application exposing REST endpoints under `/api/**`. It manages the
core domain of a trade-union membership system:

- **Members** — the central record: personal details, identity, contact, employment, banking, subscriptions,
  balances, COSATU structure involvement, and links to a recruiting representative, a representative role,
  a union structure, a job grade, and an area.
- **Banks & branches** — South African bank directory with universal branch codes, per-branch metadata, and
  physical/postal addresses.
- **Job grades** — bank-specific salary grades used to derive subscription amounts.
- **Subscriptions** — the monthly subscription schedule keyed by bank, with ACB debit day and core-banking flag.
- **Representatives & roles** — union representatives, their elected roles, and the hierarchical union
  structure tree they belong to.
- **Recruitment** — recruitment channels and recruiter (organiser) records.
- **Location reference data** — provinces, cities, towns, suburbs, and areas.
- **Deletion codes** — a self-referencing tree of reasons (main category + sub-reasons) used when removing a member.
- **Users & roles** — `User` / `UserRole` entities exist for future authentication; they are not yet wired
  into Spring Security (all `/api/**` routes are currently public — see [Security](#security-and-cors)).

Two parallel repositories make up the full system:

| Repository                                             | Stack                        | Port | Purpose            |
|--------------------------------------------------------|------------------------------|------|--------------------|
| `sasbo-online-backend` (this repo)                     | Java 21 · Spring Boot · Gradle | 8080 | REST API           |
| [`sasbo-online`](https://github.com/Fazlu101/sasbo-online) | React · Vite                | 5173 | SPA frontend       |

---

## Tech stack

| Layer            | Technology                                                        |
|------------------|-------------------------------------------------------------------|
| Language         | Java 21                                                           |
| Framework        | Spring Boot 3.4.5 (`spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`, `-thymeleaf`) |
| Build            | Gradle 9.7.1 (wrapper included)                                   |
| Persistence      | JPA / Hibernate, `ddl-auto: update`                              |
| Database         | PostgreSQL (driver `org.postgresql`)                              |
| Migrations       | Flyway 10.22.0 (`flyway-database-postgresql`)                     |
| Object mapping   | MapStruct 1.6.3 (compile-time DTO ↔ entity mappers)               |
| Boilerplate      | Lombok                                                            |
| Code style       | Checkstyle (`config/checkstyle/checkstyle.xml`)                   |
| Container        | Docker (multi-stage: `gradle:9.5-jdk21` → `eclipse-temurin:21-jre-alpine`) |
| Tests            | JUnit 5 (Spring Boot Test), H2 in-memory for test runtime         |

> The project is pure Java 21 — Kotlin sources have been fully migrated. Validate changes with
> `./gradlew build`.

---

## Architecture

The application follows a classic layered Spring Boot architecture. Each layer has a single responsibility
and a well-defined relationship to the layers around it.

```
┌─────────────────────────────────────────────────────────────┐
│  Controller layer   (controller/)   REST endpoints /api/** │
│         │  delegates to services, or injects repos directly │
│         ▼                                                    │
│  Service layer      (services/ + services/impl/)            │
│         │  business logic, validation, composition          │
│         ▼                                                    │
│  Repository layer   (repository/)   Spring Data JPA          │
│         │  JpaRepository<Entity, ID> + custom queries        │
│         ▼                                                    │
│  Entity layer       (entity/)       JPA entities + Lombok    │
│                                                              │
│  Cross-cutting:                                              │
│   • dto/ + mapper/  (MapStruct)   API contracts              │
│   • config/         Security, CORS, Jackson, request logging  │
│   • exception/      GlobalExceptionHandler                   │
│   • enums/          AccountType, PaymentType, Gender, ...    │
│   • utils/          DataSeeder, SaIdParser                   │
│   • validator/      MemberValidator                           │
└─────────────────────────────────────────────────────────────┘
```

### 1. Entity layer (`entity/`)

JPA entities mapped with Lombok. Organised by bounded context:

- `user/` — `User` (UUID primary key via `@UuidGenerator`), `UserRole`, `TwoFactorAuthentication` (`@Embedded`)
- `member/` — `Member` (the core domain model), `JobGrade`
- `bank/` — `Bank`, `BankBranch`, `Branch`, `BankingDetails`
- `location/` — `Province`, `City`, `Town`, `Suburb`, `Area`, `PhysicalAddress` (`@Embedded`)
- `representative/` — `Representative`, `RepresentativeRole`, `UnionStructure`
- `recruitment/` — `RecruitmentChannel`, `Recruiter`
- `subscription/` — `Subscriptions`
- `codes/` — `DeletionCode` (self-referencing tree)
- `BranchExecutiveCommittee`

**Conventions:**

- Primary keys use `@GeneratedValue(strategy = GenerationType.IDENTITY)` with `@Column(name = "id")`; `User`
  uses a UUID string key.
- Relationships default to `FetchType.LAZY`; `BankBranch → Bank` is `EAGER` (derived `bankName` field).
- `@Embedded` composes value objects (`PhysicalAddress`, `TwoFactorAuthentication`) into the owning table.
- Bidirectional relationships maintain symmetry through helper methods (e.g. `User.addRole()`).
- Audit timestamps via `@CreationTimestamp` / `@UpdateTimestamp` plus `@PrePersist` / `@PreUpdate` hooks.
- Derived/computed fields use `@Transient` + `@JsonProperty` (e.g. `BankBranch.getBankName()` derives from
  the parent `bank` relationship; `Member.areasRep`, `structureId`, `roleId` carry client selections that
  the service layer resolves to entity references).

### 2. Repository layer (`repository/`)

Spring Data JPA interfaces extending `JpaRepository<Entity, ID>`, with custom query methods where needed
(e.g. `findByNameIgnoreCase()`, branch search by bank name / branch / area / random access number).

### 3. DTO & mapper layers (`dto/`, `mapper/`)

Data Transfer Objects define the API contract and prevent over-exposing entity internals. MapStruct
interfaces in `mapper/` (`BankMapper`, `MemberMapper`, `ProvinceMapper`, `CityMapper`, `TownMapper`,
`SuburbMapper`, `RepresentativeMapper`, `RepresentativeRoleMapper`, `UnionStructureMapper`,
`SubscriptionsMapper`) generate the conversion implementations at compile time via the
`mapstruct-processor` annotation processor.

### 4. Service layer (`services/` + `services/impl/`)

Business logic lives behind interfaces (`BankService`, `BankBranchService`, `BankingService`,
`MemberService`, `ProvinceService`, `RepresentativeService`, `SubscriptionsService`,
`DeletionCodeService`, `JobGradeServiceImpl`, `RecruitmentChannelService`) with implementations in
`services/impl/`. Controllers that need logic delegate here; a few simpler controllers inject repositories
directly (see each controller).

### 5. Controller layer (`controller/`)

`@RestController` classes mapped under `/api/**`, using `@RequiredArgsConstructor` for constructor
injection of final dependencies. Returns DTOs (or entities for simpler read paths). See
[API reference](#api-reference) for the full list.

### 6. Config layer (`config/`)

- **`SecurityConfig`** — CORS allow-list and request authorization (see [Security](#security-and-cors)).
- **`JacksonConfig`** — JSON serialization settings.
- **`RequestLoggingConfig`** — debug request logging bean.
- **`DotEnvEnvironmentPostProcessor`** — loads `.env` and `.env.local` into the Spring environment before
  any bean is created (see [Configuration](#configuration)).

### 7. Cross-cutting

- **`exception/GlobalExceptionHandler`** — `@RestControllerAdvice` mapping exceptions to a uniform
  `ApiErrorResponse` payload (see [Error handling](#error-handling)).
- **`utils/DataSeeder`** — `ApplicationRunner` that idempotently seeds reference data on startup
  (see [Data seeding](#data-seeding)).
- **`utils/SaIdParser`** — South African ID number parsing/validation helper.
- **`validator/MemberValidator`** — payment-type and member business-rule validation.
- **`enums/`** — `AccountType`, `AddressType`, `CommunicationType`, `ContactNumberType`, `Gender`,
  `PaymentType`, `PreferredEmailType`, `SubscriptionsStatus`, `UserRole`, `VerificationType`.

---

## Project structure

```
sasbo-online-backend/
├── build.gradle                       # Dependencies, Java 21 toolchain, Checkstyle
├── settings.gradle                    # rootProject.name = 'sasbo-online-backend'
├── gradle/wrapper/                    # Gradle 9.7.1 wrapper
├── gradlew, gradlew.bat               # Wrapper scripts
├── Dockerfile                         # Multi-stage container build
├── dockerignore / gitignore
├── system.properties                  # java.runtime.version=21 (PaaS runtime hint)
├── config/checkstyle/checkstyle.xml   # Code-style rules
├── src/
│   ├── main/
│   │   ├── java/za/co/titandynamix/sasbo_online_backend/
│   │   │   ├── SasboOnlineBackendApplication.java   # @SpringBootApplication entry point
│   │   │   ├── config/        # Security, CORS, Jackson, DotEnv, RequestLogging
│   │   │   ├── controller/    # 13 REST controllers
│   │   │   ├── dto/            # DTOs by context (bank, location, member, ...)
│   │   │   ├── entity/         # JPA entities by context
│   │   │   ├── enums/          # Domain enums
│   │   │   ├── exception/      # GlobalExceptionHandler + ApiErrorResponse
│   │   │   ├── mapper/         # MapStruct mapper interfaces
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── services/        # Service interfaces + impl/
│   │   │   ├── utils/          # DataSeeder, SaIdParser
│   │   │   └── validator/      # MemberValidator
│   │   └── resources/
│   │       ├── application.yaml           # Spring config (env-var driven)
│   │       ├── data/                      # Seed SQL/JSON (deletion_codes, subscriptions)
│   │       └── db/migration/              # Flyway scripts V1–V19
│   └── test/
│       └── java/.../                      # Controller, service, validator, config tests
│           └── resources/application.yaml # Test profile config
└── AGENTS.md, HELP.md                    # Additional developer documentation
```

---

## Prerequisites

- **JDK 21** (the Gradle toolchain targets Java 21; the Docker runtime uses `eclipse-temurin:21-jre-alpine`).
- **Gradle 9.7.1** — no local install required; the `gradlew` wrapper downloads the correct distribution.
- **PostgreSQL** — a local server (e.g. via Homebrew, Postgres.app, or Docker) for development. The test
  profile uses H2 in-memory, so no database is needed to run the unit tests.
- **Git** for cloning.
- *(Optional)* **Docker** to build/run the container image.

---

## Getting started

```bash
# 1. Clone
git clone https://github.com/Fazlu101/sasbo-online-backend.git
cd sasbo-online-backend

# 2. Create a local environment override (see Configuration for why .env.local is required)
cp .env.example .env.local
#   then edit .env.local with your local PostgreSQL connection details

# 3. (Optional) start a local PostgreSQL, e.g. with Docker:
docker run --name sasbo-pg -e POSTGRES_PASSWORD=secret -p 5432:5432 -d postgres:16

# 4. Run the app
./gradlew bootRun
```

The API will be available at `http://localhost:8080/api/**` and the health check at
`http://localhost:8080/actuator/health`.

> **Note on `.env`:** the tracked `.env` file holds shared defaults only and is **not** loaded unless a
> git-ignored `.env.local` exists in the working directory. This is an intentional safety guard — see
> [Configuration](#configuration).

---

## Configuration

All runtime configuration is environment-variable driven via `application.yaml` placeholders. There is no
hardcoded connection string.

### Environment variables

| Variable                     | Purpose                                              | Required         |
|------------------------------|------------------------------------------------------|------------------|
| `SPRING_DATASOURCE_URL`      | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/db` | Yes              |
| `SPRING_DATASOURCE_USERNAME` | DB user                                              | Yes              |
| `SPRING_DATASOURCE_PASSWORD` | DB password                                          | Yes              |
| `SSL_MODE`                   | PostgreSQL `sslmode` (e.g. `require` in prod)       | Yes              |
| `SERVER_PORT`                | HTTP port (defaults to `8080`)                      | No               |
| `FRONTEND_URL`               | Read by nothing since `6c00b82`; see [CORS](#cors)   | No               |

### `.env` / `.env.local` loading

`application.yaml` reads its datasource settings from environment variables, which the host injects in
production. Locally, a custom `DotEnvEnvironmentPostProcessor` (`config/DotEnvEnvironmentPostProcessor.java`)
loads dotenv files **before any bean is created** so the placeholders resolve without exporting variables
into the shell or an IDE run configuration.

**Precedence (highest wins):**

1. Command-line arguments (`--spring.flyway.enabled=false`)
2. JVM system properties (`-D...`)
3. **`.env.local`** — machine-local, git-ignored; overrides real environment variables
4. Real environment variables (host-injected in production)
5. **`.env`** — shared committed defaults (can never mask what the host sets)
6. `application.yaml`

**The developer-checkout guard:** neither file is read unless `.env.local` exists. Because `.env.local` is
git-ignored, its presence is a reliable marker of a developer's working copy, and its absence leaves every
other environment behaving exactly as it did before — including a teammate who has not opted in, and
including production. This prevents a tracked `.env` value (e.g. `SSL_MODE=prefer`) from quietly
downgrading a production connection that should be `require`.

---

## Database

### PostgreSQL

The application uses PostgreSQL exclusively in all non-test environments. The HikariCP connection pool is
configured with a `sslmode` data-source property taken from `SSL_MODE`.

### Schema management

Two mechanisms coexist:

- **Flyway** manages versioned schema migrations under `src/main/resources/db/migration/`
  (`V1__initial_schema.sql` through `V19__add_cosatu_structure_fields.sql`). Flyway is configured with
  `baseline-on-migrate: true`, `baseline-version: 0`, and `validate-on-migrate: false`.
- **Hibernate `ddl-auto: update`** additionally auto-creates/updates schema from the entity mappings on
  startup, which is convenient for local development. (Set to `none` in hardened production if you want
  Flyway to be the sole source of truth.)

`open-in-view` is disabled (`false`) — lazy loading outside a transaction will fail fast rather than silently
open a session in the view layer.

### Migration history (highlights)

| Script | Purpose                                                                       |
|--------|-------------------------------------------------------------------------------|
| V1     | Initial schema — all base tables (users, members, banks, branches, locations, representatives, ...) |
| V2     | Update union schema                                                            |
| V3     | Add member email fields                                                        |
| V4     | Link representatives to members                                                |
| V7     | Add role description                                                           |
| V8     | Drop removed member columns (legacy cleanup)                                  |
| V9     | Drop representatives name column                                               |
| V10    | Remove `cash` payment type                                                    |
| V11    | Job grade alphanumeric                                                        |
| V12    | Add representative email fields                                                |
| V13    | Restructure representative → many structures                                   |
| V14    | Add position hierarchy to union structures                                     |
| V15    | Add member date of birth                                                       |
| V16    | Reseed union structures hierarchy                                              |
| V17    | Seed recruiters                                                                |
| V18    | Recruiter `members_recruited` to integer                                       |
| V19    | Add COSATU structure fields                                                    |

### Seed/reference SQL

`src/main/resources/data/` holds `deletion_codes.sql` and `subscriptions.sql` plus JSON data files consumed
by the `DataSeeder` (see below).

---

## Running the application

### Local development (Gradle)

```bash
./gradlew bootRun        # Run with .env.local loaded by DotEnvEnvironmentPostProcessor
./gradlew build          # Compile, run Checkstyle + tests, produce the JAR
./gradlew test           # Run unit/integration tests (H2 in-memory)
./gradlew clean          # Remove build artifacts
```

DevTools (`spring-boot-devtools`) is included as a `developmentOnly` dependency, enabling automatic restarts
during local development.

The production JAR is built to `build/libs/sasbo-online-backend-0.0.1-SNAPSHOT.jar` (the plain `-jar`
`build/...-plain.jar` is disabled via `jar { enabled = false }` so only the executable Boot JAR is produced).

### Docker

The `Dockerfile` is a multi-stage build optimised for layer caching:

```bash
docker build -t sasbo-online-backend .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/sasbo \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e SSL_MODE=prefer \
  sasbo-online-backend
```

- **Build stage:** `gradle:9.5-jdk21` resolves dependencies (cached until `build.gradle` changes), then
  builds the JAR with `./gradlew build -x test`.
- **Runtime stage:** `eclipse-temurin:21-jre-alpine` runs as a non-root `app` user, exposing port `8080`
  and starting `java -jar /app.jar`.

---

## API reference

All endpoints are rooted at `/api/**` and return JSON. Unless otherwise noted, write operations accept a
JSON request body and return the created/updated resource. See [Error handling](#error-handling) for the
error response shape.

### Members — `/api/members`

| Method   | Path                              | Description                                                                 |
|----------|-----------------------------------|-----------------------------------------------------------------------------|
| `GET`    | `/api/members`                    | List all members (returns `MemberDto[]`)                                    |
| `GET`    | `/api/members/{id}`               | Get a member by id                                                          |
| `POST`   | `/api/members`                    | Create a member (resolves structure + role; validates elected-role rules)   |
| `PUT`    | `/api/members/{id}`               | Update a member (mirrors create-time validation)                            |
| `PATCH`  | `/api/members/{id}/banking`       | Update a member's banking details                                           |
| `PATCH`  | `/api/members/{id}/subscription`  | Update a member's monthly subscription (maintenance endpoint)              |
| `GET`    | `/api/members/user_roles`         | List all representative roles (`RepresentativeRoleDto[]`)                   |
| `GET`    | `/api/members/structures`         | List all union structures (`UnionStructureDto[]`)                           |
| `GET`    | `/api/members/structures/tree`    | Position/structure hierarchy as a tree (for cascading dropdowns)            |
| `GET`    | `/api/members/metadata/payment-types` | Payment-type metadata (`name`, `displayName`, `requiresBankAccount`)  |

Member create/update business rules enforced in `MemberController`:

- If the role `requiresStructure` and no structure is assigned → `400` "Selected role requires an assigned structure."
- If the role `isElectedRole` and no `electionDate` is provided → `400` "Selected role is an elected position and requires an election date."

### Banks & branches — `/api/banks`, `/api/branches`

| Method   | Path                          | Description                                                        |
|----------|-------------------------------|--------------------------------------------------------------------|
| `GET`    | `/api/banks`                  | List all banks (`BankDto[]`)                                       |
| `GET`    | `/api/banks/branches`        | List all bank branches                                             |
| `POST`   | `/api/banks/branches`        | Create a branch from `BankBranchDto`                              |
| `PUT`    | `/api/banks/branches/{id}`   | Update a branch from `BankBranchDto`                              |
| `GET`    | `/api/branches`              | Search branches by `bankName`, `branch`, `area`, `randomAccessNumber` (all optional) |

### Subscriptions — `/api/subscriptions`

| Method   | Path                          | Description                                              |
|----------|-------------------------------|----------------------------------------------------------|
| `GET`    | `/api/subscriptions`          | List subscriptions; optional `?bankId=` filter           |
| `GET`    | `/api/subscriptions/{id}`     | Get a subscription by id                                |
| `POST`   | `/api/subscriptions`          | Create a subscription (`201 Created`)                    |
| `PUT`    | `/api/subscriptions/{id}`     | Update a subscription                                   |
| `DELETE` | `/api/subscriptions/{id}`     | Delete a subscription (`204 No Content`)                 |

### Representatives — `/api/representatives`, `/api/representative-userRoles`

| Method   | Path                                  | Description                                            |
|----------|---------------------------------------|--------------------------------------------------------|
| `GET`    | `/api/representatives`                | List all representatives (`RepresentativeDto[]`)       |
| `GET`    | `/api/representatives/{id}`           | Get a representative by id                            |
| `POST`   | `/api/representatives`                | Create a representative (400 on illegal argument)     |
| `PUT`    | `/api/representatives/{id}`           | Update a representative (404 if not found)             |
| `DELETE` | `/api/representatives/{id}`           | Delete a representative (`204`)                        |
| `GET`    | `/api/representative-userRoles`       | List representative roles                              |
| `GET`    | `/api/representative-userRoles/{id}`  | Get a role by id                                       |
| `POST`   | `/api/representative-userRoles`       | Create a role                                          |
| `PUT`    | `/api/representative-userRoles/{id}`  | Update a role (name, description, flags)              |
| `DELETE` | `/api/representative-userRoles/{id}`  | Delete a role (`204`)                                   |

### Job grades — `/api/job-grades`

| Method | Path               | Description                                                                 |
|--------|--------------------|-----------------------------------------------------------------------------|
| `GET`  | `/api/job-grades`  | List job grades; optional `?bankCode=` or `?bank=` filter. Returns `{value,label}[]` where label is `grade - description - contractType`. |

### Recruitment — `/api/recruitment-channels`

| Method   | Path                              | Description                          |
|----------|-----------------------------------|--------------------------------------|
| `GET`    | `/api/recruitment-channels`       | List all recruitment channels        |
| `POST`   | `/api/recruitment-channels`       | Create a channel                     |
| `PUT`    | `/api/recruitment-channels/{id}`  | Update a channel                     |
| `DELETE` | `/api/recruitment-channels/{id}`  | Delete a channel (`204`)             |

### Deletion codes — `/api/deletion-codes`

| Method | Path                          | Description                                                          |
|--------|-------------------------------|---------------------------------------------------------------------|
| `GET`  | `/api/deletion-codes`         | All root deletion codes with nested sub-reasons                      |
| `GET`  | `/api/deletion-codes/{id}`    | A single deletion code by id                                        |
| `POST` | `/api/deletion-codes`         | Create a deletion code (`@Valid` body; `parentId` for sub-reasons)   |

### Location reference — `/api/provinces`, `/api/areas`

| Method | Path               | Description                                  |
|--------|--------------------|----------------------------------------------|
| `GET`  | `/api/provinces`  | List all provinces (`ProvinceDto[]`)         |
| `GET`  | `/api/areas`       | List all areas (`{id,name}[]`)               |

### Other

| Path         | Notes                                                          |
|--------------|----------------------------------------------------------------|
| `/api/acb`   | `ACBController` — reserved placeholder controller (no endpoints) |
| `/actuator/health` | Spring Boot Actuator health check (publicly permitted)    |

---

## Data seeding

`utils/DataSeeder` is a Spring `ApplicationRunner` that idempotently seeds reference data on startup. It
skips a table when rows already exist (or, for subscriptions/recruitment channels, when the existing count
meets the seed size). Seeds are loaded from classpath JSON under `src/main/resources/data/`:

- **members** (`members.json`) — only when the table is empty; legacy `jobGrade` string values are dropped
  in favour of bank-specific `JobGrade` rows.
- **banks** (`banks_master.json`) and **bank branches** (`banks.json`) — branches resolve their `Bank` by
  name.
- **job grades** (`job_grades.json`) — each grade resolves its bank by name; unknown banks are skipped with a warning.
- **subscriptions** (`subscriptions.json`) — fills the gap up to the seed size; resolves bank by name.
- **recruitment channels** (`recruiter.json`) — only missing channels (by name, case-insensitive) are added.
- **recruiters** (`recruiters.json`) — only when the table is empty (the V17 Flyway migration seeds the same
  rows, so whichever runs first wins and no duplicates occur).
- **provinces** (`provinces.json`) — only when the table is empty.

Representatives are **not** seeded — they are managed via the API.

---

## Testing

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

## Error handling

`exception/GlobalExceptionHandler` is a `@RestControllerAdvice` that normalises errors into an
`ApiErrorResponse`:

```json
{
  "timestamp": "2026-09-08T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for the incoming request payload.",
  "path": "/api/members",
  "details": { "field": "default message" }
}
```

Handled exception types:

| Exception                            | Status | Notes                                              |
|--------------------------------------|--------|----------------------------------------------------|
| `EntityNotFoundException`            | 404    | Resource not found                                 |
| `MethodArgumentNotValidException`    | 400    | Bean Validation failures; `details` maps field → message |
| `IllegalArgumentException`          | 400    | Business-rule violations (e.g. elected-role checks) |
| Any other `Exception`                | 500    | Logged at `ERROR`; message echoed in the response  |

Timestamps are UTC. The catch-all 500 handler logs the full stack trace before returning.

---

## Security and CORS

### Authorization

`SecurityConfig` configures a single `SecurityFilterChain`:

- CSRF is **disabled** (the API is stateless and consumed by a SPA).
- `GET /actuator/health` is permitted.
- `GET` on `/api/banks`, `/api/banks/**`, `/api/job-grades`, `/api/subscriptions`,
  `/api/subscriptions/**` is permitted.
- **All `/api/**` routes are `permitAll()`** — the API is currently public. The `User`/`UserRole` entities
  and `TwoFactorAuthentication` embedded object exist in the model but are **not yet wired** to Spring
  Security. Treat the API as unauthenticated until authentication is implemented.

### CORS

`corsConfigurationSource()` builds an allow-list:

- Origins: `http://localhost:5173` (the Vite dev server) and nothing else. The compose stack needs no entry:
  its nginx serves the client and proxies `/api` on the one origin.
- Methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD`.
- Headers: `*`; credentials allowed; max age 3600s.
- Registered globally against `/**`.

To allow a hosted frontend, add its origin in `corsConfigurationSource()`. `CorsPolicyTest` holds the list
closed, so admitting an origin is a deliberate edit with a test to update, never an environment variable
(`FRONTEND_URL` has been read by nothing since `6c00b82`).

> ⚠️ **Getting this wrong does not look like a CORS problem.** The application starts perfectly and every
> read works — only `POST`, `PUT`, `PATCH` and `DELETE` are refused, with `403 Invalid CORS request` and an
> empty body. The client surfaces that as `Failed to save member: HTTP 403:`, which names neither CORS nor
> the origin, so it reads as a permissions bug in the application. Note that the origin the browser sends is
> the one it loaded the SPA from: inside a container network that is the service name, not `localhost`.

---

## Frontend integration

The companion SPA [`sasbo-online`](https://github.com/Fazlu101/sasbo-online) calls the API via an
`apiUrl()` utility in `src/services/api.js`:

- **Development:** the client's tracked `.env` sets `VITE_API_BASE_URL=http://localhost:8080`, so the SPA on
  `http://localhost:5173` calls the API cross-origin. That is what the CORS allow-list above admits. (The
  client has no dev-server proxy.)
- **Hosted:** none for now. The project is local only since 2026-09-22.

---

## Deployment

None for now. Since 2026-09-22 the project runs locally: it left the incumbent developer's Render and Vercel.
Run it with `./gradlew bootRun` against a local PostgreSQL (see
[Running the application](#running-the-application)), or as the image below.

When it is hosted again, the service needs the variables from the [table above](#environment-variables):

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SSL_MODE=require` (enforce TLS to a managed Postgres)
- `SERVER_PORT` if the host does not route to `8080`

and its frontend's origin added in `corsConfigurationSource()` (see [CORS](#cors)). Flyway runs on startup
(`baseline-on-migrate: true`, `baseline-version: 0`), so a fresh database is brought up to the latest schema
automatically. Hibernate `ddl-auto: update` additionally adds any new entity columns.

### Docker

See [Running the application → Docker](#docker). The image runs as a non-root `app` user on
`eclipse-temurin:21-jre-alpine` and exposes port `8080`.

---

## Contributing

1. Create a feature branch from `main`.
2. Follow the existing layering: entities → repositories → services (+ impl) → DTOs/mappers → controllers.
3. Use Lombok (`@Getter`/`@Setter`/`@RequiredArgsConstructor`), MapStruct for DTO mapping, and
   `FetchType.LAZY` unless EAGER is explicitly required.
4. Keep `/api/**` route conventions and the DTO-first API contract.
5. Run `./gradlew build` before pushing — Checkstyle and tests must pass.

### Conventions to replicate

- Lombok on entities and controllers (constructor injection via `@RequiredArgsConstructor`).
- `FetchType.LAZY` by default; EAGER only where a derived field needs the parent (e.g. `BankBranch → Bank`).
- `@Embedded` for simple value objects (`PhysicalAddress`, `TwoFactorAuthentication`).
- Audit timestamps via `@CreationTimestamp` / `@UpdateTimestamp` + `@PrePersist` / `@PreUpdate`.
- `@Transient` + `@JsonProperty` for computed fields and client selection carriers.
- DTOs for public APIs; keep entity internals private.
- New entity/endpoint checklist: entity → repository → (DTO + mapper) → service(+impl) → controller with
  `@RequestMapping("/api/<resource>")` and `@RequiredArgsConstructor`.

---

*The Java package namespace `za.co.titandynamix` is kept from the original codebase.*
