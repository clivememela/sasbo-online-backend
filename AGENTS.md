# AGENTS.md: SASBO Online Backend

**Domain**: SASBO membership management system (South African Buried Services Officers). Handles member records, bank/branch information, representatives, and administrative data.

## Architecture Overview

**Tech Stack**: Java 21 | Spring Boot 4.0.3 | Gradle | PostgreSQL | JPA/Hibernate

Two parallel project repos:
- **Backend** (`sasbo-online-backend`): Gradle-based Spring Boot REST API on port 8080
- **Frontend** (`sasbo-online`): React + Vite SPA on port 5173

Note: The backend is pure Java 21 (Kotlin sources have been migrated). Agents should validate changes with `./gradlew build`.

### Backend Layers

1. **Entity Layer** (`entity/`): JPA entities with Lombok
   - Domain models: `Member`, `User`, `Bank`, `BankBranch`, `Branch`, `Representative`, `Role`
   - Embedded value objects: `PhysicalAddress`, `TwoFactorAuthentication`
   - Uses `@Embedded` for composition; `@OneToMany`, `@ManyToOne`, `@ManyToMany` for relationships

2. **Repository Layer** (`repository/`): Spring Data JPA interfaces
   - Extends `JpaRepository<Entity, ID>`
   - Custom query methods (e.g., `findByNameIgnoreCase()`)
   - Direct dependency injection in controllers

3. **DTO Layer** (`dto/`): Data Transfer Objects for API contracts
   - MapStruct mappers in `mapper/` (e.g., `ProvinceMapper`, `CityMapper`)
   - DTOs prevent overexposing entity details

4. **Controller Layer** (`controller/`): REST endpoints at `/api/**`
   - Example: `BankController` at `/api/banks`
   - Controllers commonly delegate business logic to a service layer (`services/`) — e.g. `BankService`, `BankBranchService` (implementations live in `services/impl/`). Some controllers still inject repositories directly; check the controller to determine which pattern is used.
   - All `/api/**` routes publicly accessible (no authentication enforced)


5. **Config Layer** (`config/`):
   - **SecurityConfig**: CORS for `localhost:5173`; CSRF disabled; all /api/** permitAll()
   - **JacksonConfig**: JSON serialization settings
   - **RequestLoggingConfig**: Debug logging for requests

## Key Patterns & Conventions

### JPA Entity Design
- **Primary Keys**: `@GeneratedValue` with `@Column(name = "id")`; User.java uses UUID `@UuidGenerator`
- **Lazy Loading**: `fetch = FetchType.LAZY` (except BankBranch uses EAGER for bank relationship)
- **Lifecycle Hooks**: `@PrePersist` and `@PreUpdate` for audit timestamps
- **Embedded Objects**: `PhysicalAddress` is `@Embedded` (part of same table)
- **Relationships**: Bidirectional relationships use helper methods (e.g., `User.addRole()`, `User.removeRole()`) to maintain symmetry
- **Derived Fields**: Use `@Transient` + `@JsonProperty` for computed fields (e.g., `BankBranch.getBankName()` derives from parent `bank` relationship)

### Controller Pattern
```java
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor  // Lombok auto-generates constructor for final fields
public class ResourceController {
    private final ResourceRepository repo;
    
    @GetMapping
    public List<Resource> getAll() { return this.repo.findAll(); }
    
    @PostMapping
    public ResponseEntity<Resource> create(@RequestBody ResourceDto dto) { ... }
}
```
Controllers create entities from DTOs and persist directly; optional service layer for business logic.

### Configuration Management
- **Environment Variables** (`.env`):
  - Backend: `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`
  - Frontend: `VITE_API_BASE_URL` (proxy to `/api` in dev; built-in prod)
- **application.yaml**: Defaults to localhost; environment vars override for Render deployment

## Build & Local Development

### Backend (Gradle)
```bash
# Run application (loads .env via IntelliJ EnvFile plugin or manual export)
./gradlew bootRun

# Build production JAR
./gradlew build

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

### Frontend (Node)
```bash
# Install dependencies
npm ci

# Dev server (Vite proxy: /api → http://localhost:8080)
npm run dev

# Production build (Vite)
npm run build

# Linting
npm run lint
```

## Database & Migrations

- **Flyway** manages schema migrations (PostgreSQL driver: `flyway-database-postgresql`)
- **Hibernate DDL**: `ddl-auto: update` auto-creates/updates schema from entities (dev convenience)
- Schema location: `src/main/resources/db/migration/` (Flyway scripts)
- Local DB: PostgreSQL on localhost:5432 (or docker)

## Common Workflows

### Adding a New Entity & Endpoint
1. Create `Entity.java` in `entity/` with JPA annotations
2. Create `EntityRepository.java` extending `JpaRepository`
3. Create `EntityDto.java` in `dto/` (if DTO pattern used)
4. Create `EntityController.java` in `controller/` with `@RestController` + `@RequestMapping("/api/entity")`
5. Inject repository via `@RequiredArgsConstructor`
6. Implement GET, POST, PUT, DELETE methods

### Adding a DTO Mapper
1. Create interface in `mapper/EntityMapper.java` with `@Mapper` (MapStruct)
2. Define mapping methods: `Entity toEntity(EntityDto dto)`, `EntityDto toDto(Entity entity)`
3. Let MapStruct annotation processor generate implementation at compile-time
4. Reference in controller/service as `@Autowired EntityMapper mapper`

### Security & CORS
- **CORS** hardcoded to `localhost:5173` (dev SPA frontend)
- To allow production frontend: update `SecurityConfig.corsConfigurationSource()` with Render URL
- **Authentication**: Currently all `/api/**` routes are public; User/Role entities exist but not wired to security

## Cross-Project Communication

- **Frontend** calls `/api` via `apiUrl()` utility in `src/services/api.js`
- **Development**: Vite proxy in `vite.config.js` forwards `/api` requests to `localhost:8080`
- **Production**: Frontend env var `VITE_API_BASE_URL` set to backend Render URL
- **CORS Policy**: Frontend must be whitelisted in backend `SecurityConfig`

## Debugging & Logs

- **Backend**: SQL logging at DEBUG level; SQL bind parameters at TRACE level in `application.yaml`
- **DispatcherServlet logs**: DEBUG level (track request flow)
- **Request logging**: Enabled via `RequestLoggingConfig` bean

## Critical Files Reference

| File                                               | Purpose                              |
|----------------------------------------------------|--------------------------------------|
| `build.gradle`                                     | Gradle dependencies & Java 21 target |
| `settings.gradle`                                  | Project name                         |
| `src/main/resources/application.yaml`              | Spring config & DB connection        |
| `src/main/java/.../config/SecurityConfig.java`     | CORS & security rules                |
| `src/main/java/.../entity/Member.java`             | Core domain model                    |
| `src/main/java/.../controller/BankController.java` | REST endpoint example                |

## Known Patterns to Replicate

✅ **Use Lombok**: `@Getter`, `@Setter`, `@AllArgsConstructor`, `@NoArgsConstructor` on entities
✅ **Lazy load relationships**: `fetch = FetchType.LAZY` unless EAGER is required (BankBranch → Bank is EAGER)
✅ **Embed simple value objects**: `PhysicalAddress`, `TwoFactorAuthentication` use `@Embedded`
✅ **Audit timestamps**: `@CreationTimestamp`, `@UpdateTimestamp`, `@PreUpdate` hooks
✅ **Derived fields**: Use `@Transient` + `@JsonProperty` + helper getter methods
✅ **Controller constructor injection**: `@RequiredArgsConstructor` with final repository fields
✅ **DTOs for public APIs**: Keep entities private; expose via DTO contracts

---
*Last updated: June 22, 2026*

