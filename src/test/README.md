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
