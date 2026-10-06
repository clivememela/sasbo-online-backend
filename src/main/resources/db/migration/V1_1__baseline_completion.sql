-- ----------------------------------------------------------------------------
-- V1.1 -- what V1__initial_schema.sql does not create, and V2..V19 need.
--
-- 🔴 WHY THIS FILE EXISTS, because a fractional version is not something anyone
-- writes by choice.
--
-- This project briefly had TWO V1 migrations: `V1__initial_schema.sql` on the
-- main line, and `V1__baseline_schema.sql` written alongside the test suite.
-- Different names, same Flyway version. Git merges both cleanly and says
-- nothing -- and Flyway then refuses to start with "Found more than one
-- migration with version 1", which arrives as a failure to boot a Spring
-- context rather than as a failing test. The suite reddens everywhere at once
-- and none of the reds names the cause.
--
-- `V1__baseline_schema.sql` was deleted to resolve it, and this file supplies
-- the objects the surviving baseline lacks -- they are listed below rather than
-- counted here, because the count was wrong once already and a number in a
-- header is a number nobody re-derives. A fractional version is what lets this
-- happen without renumbering: the chain runs V1 (theirs) -> V1.1 (this) ->
-- V2 ... V19, and not one existing file is touched.
--
-- 🔑 WHAT IS HERE, and why each one is not optional. Everything ELSE in
-- V2..V19 was written defensively enough to run against a foreign baseline
-- unchanged -- `IF EXISTS` on the drops, `IF NOT EXISTS` on the adds:
--
--   branches                      `Branch.java` maps it.
--   banking_details               `BankingDetails.java` maps it.
--   bank_branches.last_visited_date  `BankBranch.java` maps it.
--   users.tfa_enabled             `TwoFactorAuthentication`, @Embedded in `User`.
--   users.send_to                 The other half of that embeddable.
--   members.job_grade             Required by V11; see the statement.
--   recruiters.members_recruited  A TYPE, not an object. See the statement.
--
-- ⚠️ A CAREFUL READ SAID THERE WERE THREE OF THESE. RUNNING IT FOUND SEVEN, and
-- that gap is the most useful thing this file records. The read was not lazy --
-- it audited every statement in V2..V19 that cannot be made conditional. It
-- missed two whole categories:
--
--   1. COLUMNS NOTHING IN THE CHAIN MENTIONS. `last_visited_date`,
--      `tfa_enabled` and `send_to` are added by NO migration. They existed only
--      because the old baseline declared them, and the only thing that knows
--      they are required is the ENTITY MODEL. Reading the migrations could not
--      have found them; `ddl-auto: validate` finds them one per run.
--   2. TYPES UNDERNEATH `IF NOT EXISTS`. That form looks like the safe one and
--      hides an assumption: it guards the TABLE and says nothing about the
--      columns of a table somebody else created. V17 creates `recruiters` with
--      `members_recruited VARCHAR(255)` and seeds '10', '11', '13'; V18 then
--      converts it to INTEGER. Against the surviving baseline the table already
--      exists with that column INTEGER, the create no-ops, and the seed fails
--      with `column "members_recruited" is of type integer but expression is of
--      type text` -- 65 integration tests failing with a context-load error
--      that names Spring rather than Flyway.
--
-- 🔑 The complete list came from diffing the two baselines column by column
-- rather than from a sixth run. `validate` reports ONE missing column per
-- attempt, so following it would have cost a container start each time and
-- still not have said when it was finished.
--
-- ⚠️ EVERY STATEMENT HERE IS IDEMPOTENT, and that is a requirement rather than
-- a style. The runtime configuration sets `baseline-on-migrate: true`, so this
-- migration will be applied to databases that already exist and already have
-- some of these objects -- not only to the empty one the integration lane
-- builds. A bare `CREATE TABLE` here would break every deployed database.
--
-- ⚠️ TWO COSTS WERE ACCEPTED KNOWINGLY when the surviving baseline changed, and
-- they are recorded so that nobody later reads them as bugs:
--
--   1. Some of what `MigrationChainContentTest` proved about the V8 and V9 drops
--      is gone. That test owns the fact and states it; it is deliberately NOT
--      restated here, because a count in a second file is a count that drifts.
--   2. This file is meaningless without `V1__initial_schema.sql` staying as it
--      is. Under `validate-on-migrate: true` -- which the migration test profile
--      sets deliberately -- an edit to that file changes its checksum and
--      reddens this suite for a change made elsewhere. That is the price of not
--      needing anyone's permission to unblock the merge, and it is reversible:
--      if the two baselines are ever reconciled upstream, delete this file.
-- ----------------------------------------------------------------------------

-- `branches` -- bank branches, as distinct from `bank_branches`, which is the
-- join table. Column types match `banks`/`cities`/`provinces`/`suburbs` as the
-- surviving baseline declares them: SERIAL is INTEGER and BIGSERIAL is BIGINT,
-- so these foreign keys resolve against either baseline.
CREATE TABLE IF NOT EXISTS branches
(
    id                  INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    branch_code         VARCHAR(6)   NOT NULL,
    branch_name         VARCHAR(100) NOT NULL,
    complex_or_building VARCHAR(100),
    postal_code         VARCHAR(10),
    street_name         VARCHAR(100),
    street_number       VARCHAR(20),
    bank_id             INTEGER      NOT NULL,
    city_id             BIGINT,
    province_id         BIGINT,
    suburb_id           BIGINT,
    CONSTRAINT fk_branches_bank FOREIGN KEY (bank_id) REFERENCES banks (id),
    CONSTRAINT fk_branches_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT fk_branches_province FOREIGN KEY (province_id) REFERENCES provinces (id),
    CONSTRAINT fk_branches_suburb FOREIGN KEY (suburb_id) REFERENCES suburbs (id)
);

-- `banking_details` -- standalone; nothing in `members` points at it, and it
-- points at `banks` and `branches` rather than the other way round. That is why
-- it can be created here, after `members` already exists.
CREATE TABLE IF NOT EXISTS banking_details
(
    id                  INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_holder_name VARCHAR(255) NOT NULL,
    account_number      VARCHAR(255) NOT NULL,
    account_type        VARCHAR(255) NOT NULL,
    bank_id             INTEGER      NOT NULL,
    branch_id           INTEGER      NOT NULL,
    CONSTRAINT banking_details_account_type_check
        CHECK (account_type IN ('CHEQUE', 'SAVINGS', 'TRANSMISSION')),
    CONSTRAINT fk_banking_details_bank FOREIGN KEY (bank_id) REFERENCES banks (id),
    CONSTRAINT fk_banking_details_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
);

-- The three columns NO migration adds, and which exist only because the old
-- baseline declared them.
--
-- 🔑 THESE ARE THE ONES READING THE MIGRATIONS CANNOT FIND. Nothing in V2..V19
-- mentions any of them; the only statement that they are required is in the
-- Java. `BankBranch.lastVisitedDate` is a plain mapped field, and
-- `TwoFactorAuthentication` is @Embedded in `User`, contributing `tfa_enabled`
-- (it names the column explicitly) and `send_to` (an @Enumerated(STRING), so
-- text). Miss one and the chain runs perfectly and then `ddl-auto: validate`
-- refuses the context with `missing column [...] in table [...]`.
ALTER TABLE bank_branches
    ADD COLUMN IF NOT EXISTS last_visited_date VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS tfa_enabled BOOLEAN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS send_to VARCHAR(255);

-- `members.job_grade` -- the free-text grade that `job_grade_id` replaced.
--
-- 🔴 NOTHING READS THIS COLUMN and it must exist anyway. V11's ALTER COLUMN ...
-- TYPE has no `IF EXISTS` in Postgres and fails hard on a missing column, so
-- omitting this does not skip a step -- it stops the chain at V11 and every
-- migration after it. Against this baseline V11 becomes a no-op type change
-- (the column is created as its target type below); it still does real work on
-- the ddl-auto databases in the field, where the column may be numeric.
--
-- It ends the chain vestigial. Hibernate `validate` ignores columns the entity
-- model does not mention, so it is harmless -- it is simply dead. Leave it.
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS job_grade VARCHAR(255);

-- `recruiters.members_recruited` -- widened back to text so that V17 can seed it.
--
-- 🔴 THIS ONE IS A TYPE, NOT AN OBJECT, and that is why reading missed it.
-- V17 does `CREATE TABLE IF NOT EXISTS recruiters (... members_recruited
-- VARCHAR(255) ...)` and then seeds '10', '11', '13' as text; V18 converts the
-- column to INTEGER afterwards. The whole of that sequence assumed V17 was the
-- statement that CREATED the table. Against the surviving baseline it is not --
-- the table is already there with the column already INTEGER, the create
-- no-ops, and the seed fails with "column members_recruited is of type integer
-- but expression is of type text".
--
-- 🔑 So the chain's end state is unchanged and only its middle moves: this
-- widens the column, V17 seeds text into it exactly as it always did, and V18
-- narrows it back to INTEGER with its own USING clause. `recruiterCountsAreNumeric`
-- still asserts `integer` at the end, and it is the guard that would catch this
-- being wrong.
--
-- ⚠️ NOT fixed by editing V17. V17 and V18 are shared with the main line
-- unchanged, and editing an applied migration changes its checksum -- which,
-- under the `validate-on-migrate: true` this suite runs with, reddens every
-- database that already has it. Fixing the precondition here keeps every shared
-- file untouched, which is this whole file's premise.
-- H2 and PostgreSQL both support this ALTER COLUMN form. The migration runs
-- before V17, so the seed can insert its text values and V18 can restore the
-- final INTEGER type.
ALTER TABLE recruiters
    ALTER COLUMN members_recruited TYPE VARCHAR(255);
