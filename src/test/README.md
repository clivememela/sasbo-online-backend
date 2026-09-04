# The server test suite — how it is built, and what to copy

This is the file to read before adding a test here. It has two parts:

1. **The audit** — what was found in the tests that predate this suite's rework,
   and the verdict on each. Read it once; it is why some things were rewritten.
2. **The pattern** — the shape a new domain copies. *(Added with the `bank`
   reference module.)*

---

## Part 1 — The audit of the 42 pre-existing tests

**Run before any new test was written**, deliberately. The suite is built as
*"one reference module, every other domain copies it"* — so a reference that
asserts its own stubs would propagate that into eight domains. The copies would
be this suite's own output, not inherited debt, which is why the audit is a gate
and not a courtesy.

### The method — mechanical, not editorial

> **Replace the production method body with a trivial pass-through and re-run.
> If the test still passes, it was asserting the mock.**

This is cheaper and far more honest than reading each test and forming an
opinion. A test that survives the removal of the behaviour it names was never
testing that behaviour.

### The substitution that was run, and its result

`BankServiceImpl.findAll()` really is:

```java
return this.bankRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
```

It was replaced with the trivial pass-through — the same call with the sort
removed:

```java
return this.bankRepository.findAll(Sort.unsorted());   // AUDIT: gutted
```

`./gradlew test --tests '*BankServiceImplTest*' --rerun` → **9 tests, 0 failures,
0 errors. All nine still passed with the sort gone**, including:

```
  pass  Should return banks sorted by name in ascending order
  pass  Should call repository.findAll with Sort.ASC on name field
  pass  Should return all banks from repository with descending sort order support
```

**Three test names make a claim about sort order that survives the sort being
deleted.** Every one of them stubs `findAll(any(Sort.class))` with an
already-ordered list and then asserts that list is ordered — which proves that
Mockito returns what it was told to return. The substitution was reverted; the
production file is unchanged.

> **`any()` may not stand behind a claim of specificity.**
> `verify(repo).findAll(any(Sort.class))` under the name *"should call findAll
> with Sort.ASC on name"* asserts nothing about the sort. Where the claim is
> about **what** a collaborator was called with, an `ArgumentCaptor` makes it and
> the captured value is asserted.

### The 42, by class — every verdict

| Class | Tests | Verdict | Why |
| :-- | :-: | :-- | :-- |
| `SasboOnlineBackendApplicationTests` | 1 | **adopted** | `contextLoads()`. Proves the context starts; claims nothing else, and its name does not overstate it |
| `DotEnvEnvironmentPostProcessorTest` | 14 | **adopted** | The strongest pre-existing file. Tests a real algorithm against real inputs, with no mocks to assert |
| `DeletionCodeControllerTest` | 3 | **adopted — and promoted to the reference** | See below |
| `MemberControllerTest` | 7 | **rewritten** | Mocks `MemberMapper`, so every `jsonPath` content assertion returns the value the test stubbed. No write endpoint checks what the JSON bound to |
| `JobGradeServiceTest` | 5 | **adopted — the reference for service tests** | See below |
| `BankServiceImplTest` | 9 | **5 rewritten · 2 deleted · 2 adopted** | See the per-test table |
| `MemberValidatorTest` | 3 | **adopted, one gap added** | Real static logic, no mocks. Missing the value the client actually sends |
| **Total** | **42** | | |

### `DeletionCodeControllerTest` — the reference for controller tests

It was the strongest test in the repository and nobody had said so. Four things
make it the pattern the other eleven controllers copy:

- It uses **real DTOs** rather than a mocked mapper, so its `jsonPath`
  assertions mean something.
- It **exercises `GlobalExceptionHandler`** on both the 404 and the 400 path,
  rather than asserting a status the controller returned directly.
- It asserts the **error envelope shape** — `$.status`, `$.error`,
  `$.validationErrors.name`.
- It builds its payload with `objectMapper.writeValueAsString(dto)` instead of a
  hand-typed JSON string, so a renamed field breaks the test at compile time.

### `JobGradeServiceTest` — the reference for service tests

It tests real branching — universal branch code → bank id → bank name — and it
uses **negative** assertions (`never()`, `verifyNoInteractions`) to prove a path
was *not* taken. A test that only ever asserts what did happen cannot tell you
the fallback did not fire.

### `BankServiceImplTest`, test by test

| Test | Verdict | Why |
| :-- | :-- | :-- |
| `testFindAllEmpty` | **rewritten** | Stubs empty, asserts empty. The assertion is the stub |
| `testFindAllSingleBank` | **rewritten** | Stubs one bank, asserts one bank |
| `testFindAllSortedByName` | **rewritten** | Stubs a pre-sorted list and asserts it is sorted. Survives the sort being deleted |
| `testFindAllCallsRepositoryWithCorrectSort` | **rewritten** | Its name claims `Sort.ASC` on `name`; it asserts `any(Sort.class)`. Rewritten with an `ArgumentCaptor<Sort>` asserting `Sort.by(ASC, "name")` |
| `testFindAllUsesRepository` | **deleted** | Duplicates `testFindAllSingleBank` at a different size. Its name — *"with descending sort order support"* — describes behaviour that does not exist |
| `testFindByNameIgnoreCase` | **adopted** | Stubs on an exact argument, so it does prove delegation |
| `testFindByNameIgnoreCaseNotFound` | **adopted** | Same, for the empty case |
| `testSaveNewBank` | **rewritten** | Asserts the fields of the object the stub returned. Rewritten to capture the argument passed to `save` |
| `testUpdateBank` | **deleted** | Identical code path to `testSaveNewBank` — `save` has no update branch. Two names, one path |

**A `@DisplayName` is a claim, and the assertions must cover it.** A name that
overstates the assertion is worse than no name: it is the thing a reviewer reads
instead of the code.

### `MemberValidatorTest` — the gap it leaves

Its three tests are sound. What is missing is the case that actually happens in
production: the browser sends **`'Payroll'`**, and the only payroll test here
covers **`'Payroll Deduction'`**, a legacy shim. The existing test is correctly
named for the shim; a test for the value the client really sends is added with
the `member` domain.

> A test suite that only covers the legacy value **encodes the drift as the
> specification.**

### `.ifPresent` — the audit's other half

`.ifPresent()` with no `else` is *"a field that reads but does not write"* in its
purest form: a lookup that misses writes nothing, logs nothing and returns
success. `grep -rn "ifPresent" src/main/java` — **12 hits, 3 of them safe:**

| Location | Hits | Status |
| :-- | :-: | :-- |
| `MemberServiceImpl` lines 82, 89, 115 | 3 | **`ifPresentOrElse`** — the miss is handled explicitly, by nulling the field |
| `MemberServiceImpl` lines 197, 199, 219, 248, 263 | 5 | bare `ifPresent` — covered by tests that assert the miss is not silent |
| `SubscriptionsServiceImpl` lines 48, 65 | 2 | bare `ifPresent` — same |
| `DataSeeder` lines 94, 141 | 2 | **the known silent bank-drop.** A subscription whose bank name does not match exactly is written with a blank bank, no log and no error. One such row is in the live database today |

The `DataSeeder` case is asserted by a deliberately disabled test, not fixed —
fixing it is a production change and this suite's job is to make the defect
visible, not to repair it.

### What the audit did *not* change

Nothing under `src/main/java`. The substitution was applied, measured and
reverted. Every verdict above is executed in the sub-branch that owns that
domain — `bank` with the reference module, `member` with the member domain — so
that each rewrite is reviewed beside the pattern it is being brought up to.

---

## Part 2 — The pattern

**`bank` is the reference module.** It was built first and alone, and every
other domain copies it rather than reinventing a shape. If something here does
not fit your domain, that is worth saying out loud — the answer is usually that
the harness needs a change, not that this domain is special.

### The six files per domain

| File | Lane | Proves | May **never** assert |
| :-- | :-- | :-- | :-- |
| `<Domain>ValidationTest` *(where a validator exists)* | fast | The rules, as plain static calls | Anything needing a framework |
| `<Domain>MapperTest` | fast | Every target field arrives, **by reflection** | Anything the database decides |
| `<Domain>ServiceTest` | fast | Business rules and branching | Anything a stub supplied |
| `<Domain>ControllerTest` | fast | Transport, binding, status, the error envelope | Authorization, unless it imports the real `SecurityConfig` |
| `<Dto>JsonTest` | fast | The exact wire shape the client parses | What the server does with it |
| `<Domain>RepositoryIT` | integration | SQL, constraints, ordering, collation | UI behaviour |

**A fact is proven at the cheapest layer that can prove it, and proven there
once.** Re-proving a lower layer from a higher one is how a suite gets slow and
brittle without getting better.

### The four rules that are not negotiable

**1. No test asserts a value its own stub supplied.** If the arrange block
returns a sorted list and the assert block checks the list is sorted, the test
has proved that Mockito works. This is the single most common way a suite
reaches a high test *count* while asserting nothing, and the audit above found
five instances of it in one file.

**2. `any()` may not stand behind a claim of specificity.** Where the claim is
about *what* a collaborator was called with, an `ArgumentCaptor` makes it and
the captured value is asserted.
`BankServiceImplTest.findAllRequestsAscendingNameOrder` is the reference.

**3. Never assert a human-readable message.** Status codes and field names are
the contract; messages are copy. A test that reddens when someone rewords a
`throw` is a test that punishes an improvement.

> **What this codebase actually guarantees, which is less than the rule
> assumes.** The `exception` package holds exactly two files — the handler and
> the `ApiErrorResponse` record. There are **no custom exception types and no
> `code` field anywhere.** The envelope is `timestamp · status · error ·
> message · path · validationErrors`, where `error` is the HTTP reason phrase
> and `message` is `ex.getMessage()`. So the three things that are stable, and
> therefore the only three worth asserting, are: **the HTTP status**, the
> **`validationErrors` field names**, and **the envelope's own shape**. A typed
> `code` per domain failure is the right design and it is a production change;
> it is written up as a finding, not built here.

**4. The H2 fast lane is not a database.** Its schema is one Hibernate invents
at startup — `create-drop`, Flyway disabled. Fine for transport, mapping and
rules; **never** evidence about schema, constraints, native SQL, `ILIKE`,
sequences, collation or `ORDER BY`. Anything in that list is an `*IT`.

### Fixtures

One builder per domain in `fixtures/`, returning a **valid baseline**. A test
overrides only the field it is about, so its intent is visible in one line and
it does not redden when an unrelated required column is added. The same builder
serves both lanes — two fixture sets for one domain are two specifications.

**Synthetic values only, never a seeded real one.** Coverage reports, failure
output and traces are artefacts that get forwarded; a real SA ID in a fixture is
a privacy breach with a URL attached.

**Any name that can reach an `ORDER BY` is collation-neutral**: plainly
separated ASCII, no leading lowercase particle (`de Villiers`, `van Zyl`), no
apostrophe or hyphen (`O'Brien`, `Smit-Nel`), no diacritic, no case-only pair.
Those are exactly the names `C`, `en_ZA.UTF-8` and `en_US.UTF-8` disagree about,
and they are ordinary South African names — which is why this is a rule and not
an intention. Realism is the wrong instinct: a sort test proves the `ORDER BY`
clause, and the names are an implementation detail of that proof.

### Mapper tests are field-exhaustive, and have no list

`MapperContract.assertEveryTargetFieldArrives(...)` populates every source field
by reflection, maps, then walks the **target** type demanding each field
arrived. Copy `BankMapperTest` and change three things: the mapper, the two
types, the floor.

A target field with no source **fails by name**. If it genuinely has none, it
goes in `knownUnmapped` *with a written reason* — and the guard then asserts it
is still `null`, so the day someone maps it the test reddens and the entry has
to go. A subtraction list that silently absorbs entries hides its third one.

> **What does and does not redden it.** Adding a field to the DTO alone goes red
> naming that field. Adding it to the entity **as well** does not — MapStruct
> maps same-named fields implicitly, so that change is genuinely correct and the
> guard is right to stay green.

### Integration tests share one real database, and must say so

One container per JVM, and test classes run concurrently. `DatabaseReset` is a
`TRUNCATE` across every table, so a class resetting while a sibling reads a
count makes that sibling see zero.

Every `*IT` touching table data therefore declares
`@ResourceLock(DatabaseReset.SHARED_DATABASE)` — `READ_WRITE` if it resets or
writes, `READ` if it only reads. **A lock is a confession**, and each one says
in a comment which it is and why. The cheaper answer is always a test that needs
no lock: transactional and rolled back, or keyed uniquely enough that a sibling
cannot collide with it.

### What adding a new domain costs the harness

**Nothing.** Write the six files; the discovery guards pick the domain up
because it exists, not because someone remembered to register it. Any answer
other than "nothing" is a design defect in the harness, and the fix belongs to
whoever owns the harness — not to whoever tripped over it.

---

## Part 3 — Findings: what this suite asserts but does not fix

**This suite makes defects visible. It does not repair them.** Every row below is
a production change, and a testing pass that quietly rewrites application code is
a change nobody asked for. Two kinds are listed:

- **Asserted** — a test written to the correct behaviour, landed `@Disabled`,
  citing its row in `KnownDefects`. It turns green the day the defect is fixed,
  and `DisabledPopulationTest` then reddens until the row is removed.
- **Filed** — recorded here only. Either there is nothing a test can assert
  without inventing the design, or the fix is a decision rather than a repair.

### Asserted, and deliberately red

| Finding | Where | Asserted by |
| :-- | :-- | :-- |
| The catch-all handler copies `ex.getMessage()` into the response body, so a 500 can carry SQL, table and column names and bound parameter values. With `/api/**` on `permitAll()`, an anonymous caller who can trigger a 500 reads them | `GlobalExceptionHandler.handleGeneralException` | `GlobalExceptionHandlerTest` |
| The validation handler casts **every** binding error to `FieldError`. A class-level constraint produces an `ObjectError`, so the 400 path throws `ClassCastException` and the caller gets a 500. Latent until the first class-level constraint is written — **found by the first test ever run against this handler** | `GlobalExceptionHandler.handleValidationFailures` | `GlobalExceptionHandlerTest` |
| Service methods issue more than one repository write with no transaction boundary. **2 of 19 services** carry `@Transactional`; a failure between writes leaves data half written, with nothing logged | `services/impl/` | `TransactionBoundaryTest` |
| No list endpoint paginates or caps its result. Every one returns the whole table, so the response grows with the database. `GET /api/members` is the clearest case | `controller/` | `UnboundedCollectionTest` |

### Filed, not asserted

| Finding | Detail |
| :-- | :-- |
| **No typed error codes anywhere** | The `exception` package holds two files. The envelope's `error` is the HTTP reason phrase and `message` is `ex.getMessage()` — so a test can assert the status, the `validationErrors` field names and the envelope shape, and nothing else. A typed `code` per domain failure is the right design; it needs new exception types and a new envelope field, which is a production change |
| **`@Data` over PII on two response DTOs** | `MemberDto` (identity number, passport number, date of birth, bank account number) and `BankingDetailsDto` (account number) generate a `toString()` over those fields. One log format argument away from the log file. Asserted as a *list* by `PiiInLogsTest`, so a **new** type doing the same reddens immediately |
| **`root: DEBUG` plus a payload-logging filter** | `RequestLoggingConfig` mounts `CommonsRequestLoggingFilter` with `setIncludePayload(true)`, and the root level is `DEBUG`. What stops every POST body — identity numbers included — reaching the log today is a *single line* setting that one logger to `WARN`. That is a configuration accident standing in for a design |
| **Two controllers bypass the service layer** | `AreaController` and `RepresentativeRoleController` inject a repository directly and map rows inline. Asserted as a list by `LayeringTest`, so a third would redden |
| **`DataSeeder` drops an unmatched bank silently** | Exact name matching plus `.ifPresent()` with no `else`: a subscription whose bank name does not match is written with a blank bank, no log and no error. One such row is in the live database |
| **`SaIdParser` reads the wall clock** | Century inference calls `LocalDate.now()` inside a `static` method, so the same input yields a different answer as years pass and no test can pin the rollover without moving the machine's clock. The fix is to inject a `Clock` |
| **`POST /api/members` returns `200`, not `201`, with no `Location` header** | Wrong by REST convention. It is asserted **as it is**, with the intended shape named in a comment — the one deliberate assertion of current behaviour here, allowed only because the status code is load-bearing for the browser. A test asserting `201` would be red from day one for something nobody is going to fix in a testing pass, and a permanently-red box teaches everyone to ignore red boxes |

> **Why `POST /api/members` is the single exception to "never assert the
> defect".** Untested, it is invisible. Asserted as-is with the intent written
> down, the day someone *does* fix it the test goes red and points straight at
> its own comment. A defect nobody notices being fixed is the failure a register
> exists to prevent.
