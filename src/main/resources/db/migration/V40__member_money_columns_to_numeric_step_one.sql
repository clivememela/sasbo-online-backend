-- ---------------------------------------------------------------------------------------------
-- The member's four money columns gain NUMERIC(12,2) twins, kept in step both ways. STEP ONE OF TWO.
--
-- WHY THIS EXISTS
-- `monthly_subscription`, `current_balance`, `arrears_balance` and `last_month_balance` are
-- VARCHAR(255) from V1. Text cannot be summed, compared or ordered as money: '100.00' < '9.00' is
-- true in this database today. The ACB module's whole output is a control total -- a count and a
-- rand value the bureau reconciles against -- and a control total computed over text is not one.
--
-- 🔴 WHY THIS ADDS COLUMNS INSTEAD OF RENAMING THEM. READ THIS BEFORE "SIMPLIFYING" IT.
-- The first version of this migration renamed each text column to `*_text` and gave the ORIGINAL
-- name to the new NUMERIC column. That is smaller, and it let the entity move to its final column
-- names once instead of twice. It was wrong, and the deploy session found it (blast-radius F38):
--
--   Render moves traffic to a new instance only once that instance passes its health check, and
--   Flyway runs at BOOT -- before the check. So there is a window, on the normal deploy path and
--   not an unlucky one, in which this migration has COMMITTED while the OLD build is still the one
--   serving. Renaming retypes the exact column that old build writes to. Measured on PostgreSQL
--   18.3: an INSERT of a parameter declared `varchar` into a `numeric` column fails outright --
--   ERROR 42804. So every member save from the old instance would have failed for the whole window.
--
-- The same wall is hit by any LATER start of an old build -- a rollback, or an idle instance waking
-- on a previous deploy -- which is why this shape is right regardless of the service's plan tier.
--
-- 🔴 AND WHY THE TWO SIDES ARE KEPT IN STEP BY A TRIGGER. Adding columns alongside is not enough on
-- its own: it swaps a LOUD failure for a SILENT one. During the overlap the old build writes the text
-- column and never touches the numeric one, so the numeric twin goes stale -- and the new build then
-- serves the stale value, with nothing failing anywhere. And after the overlap the new build writes
-- only the numeric column, so a ROLLBACK would show every member the value they had before the
-- deploy. A trigger, created at the END of this file, copies whichever side a writer changed onto the
-- other. Each build writes only its own side -- the old one does not know the numeric column exists,
-- and the new one no longer maps the text -- so the trigger can always tell which side is newer.
-- V49 drops the trigger first, before anything else.
--
-- 🔴 IT IS WRITTEN TO BE READ AT 02:00 DURING A DEPLOY, WHICH IS THE ONLY TIME IT MATTERS.
-- Every statement names its columns literally, so `grep arrears_balance` answers "what happens to
-- this column" in one screen, and any single statement can be copied into psql and run by hand. An
-- earlier version looped over the column names with EXECUTE format(), which was shorter and could
-- not be grepped, dry-run, or re-run one column at a time.
--
-- 🔴 AND IT WRITES EACH ROW ONCE. The backfill is ONE UPDATE over all four columns rather than four
-- passes. At 65,000 members four passes would leave four extra row versions each -- roughly 136 MB
-- of dead tuples and 780,000 index entries -- and autovacuum would be reclaiming that for tens of
-- minutes while the live application reads a five-times-bloated table. It also holds ACCESS
-- EXCLUSIVE on `members` for four times as long: Flyway runs a migration in ONE transaction, so the
-- lock taken by the first ALTER below is held until this file ends.
--
-- THE RULE, STATED ONCE -- and nothing is silently nulled
--   NULL                          -> NULL. There was no value; nothing is lost and nothing is
--                                   reported, or every empty column on every member is a report row.
--   '' or whitespace              -> NULL, reported as 'blank'.
--   matches ^-?[0-9]+(\.[0-9]+)?$ -> copied across, rounded to two decimals.
--   anything else                 -> NULL, reported as 'unparseable'. The raw text is untouched in
--                                   its original column, so this migration destroys nothing -- a
--                                   human decides what '174,00' meant.
--   more than two decimals        -> copied across rounded, reported as 'rounded', because rounding
--                                   changes a money value and a change nobody can see is the kind
--                                   this plan exists to avoid.
--
-- 🔑 A comma is NOT read as a decimal separator. '174,00' is reported rather than guessed at: read
-- one way it is 174.00 and the other 17400, and money is not the place to guess.
-- ---------------------------------------------------------------------------------------------

-- A blocked lock must fail this migration, not hang the boot. Flyway runs at application start on a
-- container whose health check has its own patience; without this, one session holding ACCESS SHARE
-- on `members` stalls the deploy with no error anywhere.
--
-- 🔑 It is a SAFETY property, not hygiene (the deploy session's point): a migration that fails at
-- boot fails the deploy, Render cancels it, and the OLD build keeps serving. That is the outcome we
-- want from a bad night.
SET LOCAL lock_timeout = '5s';

-- The rule above, as code, so the places that need it cannot drift from each other. Kept after this
-- migration deliberately: the trigger below uses it on every old-build write, and V49 uses it too.
-- 🔑 It PARSES; it does not round. The rounding happens at the call sites that want it, because the
-- report has to be able to ask "did rounding change this value?" -- which is `parsed <>
-- round(parsed, 2)`, and is unaskable if the parse has already rounded.
--
-- The CASE is what keeps the cast off unparseable text: PostgreSQL does not guarantee short-circuit
-- evaluation of AND/OR, so `raw ~ shape AND raw::NUMERIC ...` may attempt the cast on 'abc'. CASE
-- evaluates its arms in order, and is the only safe form here.
CREATE FUNCTION money_text_as_numeric(raw TEXT) RETURNS NUMERIC
    LANGUAGE sql IMMUTABLE AS
$$
SELECT CASE WHEN btrim(raw) ~ '^-?[0-9]+(\.[0-9]+)?$' THEN btrim(raw)::NUMERIC END
$$;

COMMENT ON FUNCTION money_text_as_numeric(TEXT) IS
    'The money-text parse rule of V40, stated once: trimmed, digits with at most one decimal point; '
    'NULL for anything else, including comma decimals. It does not round -- callers do.';

-- What this migration could not copy verbatim, kept so a human can decide what each value meant.
--
-- ⚠️ It is a RECORD, not a gate. It cannot tell anyone whether a value has since been fixed -- a row
-- written here stays written. V49 decides whether the text columns may be dropped by asking the
-- DATA, which is derivable at any moment and self-heals the instant somebody corrects a member.
--
-- 🔴 THE GATE V49 MUST USE is `members_money_only_in_text()`, defined below the columns: the number
-- of members holding a NON-BLANK text value with NO numeric twin -- the values that exist only as
-- text, and would be destroyed by dropping it. Zero means the text is safe to drop.
--
-- It is a FUNCTION rather than SQL in this comment, deliberately. An earlier version of this header
-- gave the gate as `round(money_text_as_numeric(text), 2) IS DISTINCT FROM numeric`, in a comment, and
-- it was BLIND to exactly the values it exists to protect: for 'abc' both sides are NULL, NULL IS
-- DISTINCT FROM NULL is false, so an unparseable value was never counted and V49 would have dropped
-- it. It was even "verified" once, returning 0 over fixtures that included 'abc' -- a zero that meant
-- the gate could not see, not that nothing needed a human. A gate written in a comment is a gate no
-- test can call; this one is called by V49 and by MemberMoneyMigrationIT, which asserts it COUNTS
-- those rows rather than merely returning zero.
CREATE TABLE members_money_migration_report (
    id          BIGSERIAL PRIMARY KEY,
    member_id   INTEGER     NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    column_name TEXT        NOT NULL,
    raw_value   TEXT,
    outcome     TEXT        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE members_money_migration_report IS
    'Every member money value V40 did not copy across verbatim -- blank, unparseable, or rounded to '
    'two decimals -- named for the text column that still holds the raw value. A record for a human '
    'to work from, not a gate: see the migration header for the gate V49 actually uses.';

-- ── The new columns ───────────────────────────────────────────────────────────────────────────
-- Catalog-only: ADD COLUMN with no default has rewritten nothing since PostgreSQL 11.
--
-- 🔴 THE FOUR VARCHAR COLUMNS ARE NOT TOUCHED. Not renamed, not retyped, not dropped. That is the
-- whole point of this file and the reason it is longer than it needs to be.

ALTER TABLE members
    ADD COLUMN monthly_subscription_numeric NUMERIC(12,2),
    ADD COLUMN current_balance_numeric      NUMERIC(12,2),
    ADD COLUMN arrears_balance_numeric      NUMERIC(12,2),
    ADD COLUMN last_month_balance_numeric   NUMERIC(12,2);

COMMENT ON COLUMN members.monthly_subscription_numeric IS
    'Transitional. V49 drops members.monthly_subscription and renames this to it.';
COMMENT ON COLUMN members.current_balance_numeric IS
    'Transitional. V49 drops members.current_balance and renames this to it.';
COMMENT ON COLUMN members.arrears_balance_numeric IS
    'Transitional. V49 drops members.arrears_balance and renames this to it.';
COMMENT ON COLUMN members.last_month_balance_numeric IS
    'Transitional. V49 drops members.last_month_balance and renames this to it.';

-- The gate V49 must pass, stated once as code (see the header). `nullif(btrim(x), '')` is how a blank
-- is told apart from a value: a blank text has nothing in it to lose, so it is not counted.
CREATE FUNCTION members_money_only_in_text() RETURNS BIGINT
    LANGUAGE sql STABLE AS
$$
SELECT count(*)
  FROM members
 WHERE (monthly_subscription_numeric IS NULL AND nullif(btrim(monthly_subscription), '') IS NOT NULL)
    OR (current_balance_numeric      IS NULL AND nullif(btrim(current_balance), '')      IS NOT NULL)
    OR (arrears_balance_numeric      IS NULL AND nullif(btrim(arrears_balance), '')      IS NOT NULL)
    OR (last_month_balance_numeric   IS NULL AND nullif(btrim(last_month_balance), '')   IS NOT NULL)
$$;

COMMENT ON FUNCTION members_money_only_in_text() IS
    'V49''s gate: members holding a non-blank money text with no numeric twin. V49 refuses to drop '
    'the text columns unless this is zero. Called by V49 and by MemberMoneyMigrationIT.';

-- ── What will not be copied ───────────────────────────────────────────────────────────────────
-- Reported BEFORE the backfill. One statement per column: each is readable on its own and names the
-- column it is about -- the ORIGINAL column, because that is where the raw value lives and it is
-- what V49 drops.

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'monthly_subscription', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(monthly_subscription)                      AS raw,
               money_text_as_numeric(monthly_subscription)      AS parsed
          FROM members
         WHERE monthly_subscription IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'current_balance', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(current_balance)                      AS raw,
               money_text_as_numeric(current_balance)      AS parsed
          FROM members
         WHERE current_balance IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'arrears_balance', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(arrears_balance)                      AS raw,
               money_text_as_numeric(arrears_balance)      AS parsed
          FROM members
         WHERE arrears_balance IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'last_month_balance', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(last_month_balance)                      AS raw,
               money_text_as_numeric(last_month_balance)      AS parsed
          FROM members
         WHERE last_month_balance IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

-- ── The backfill ──────────────────────────────────────────────────────────────────────────────
-- ONE pass, so each row is rewritten once. A row with no text in any of the four columns is left
-- alone entirely rather than rewritten to set four NULLs.
--
-- ⚠️ It runs BEFORE the trigger exists, and must. The trigger copies a changed numeric back onto the
-- text; if it were live here, this very UPDATE would rewrite ' 174 ' as '174.00' and '174.005' as
-- '174.01' -- destroying the raw text the report and V49 both depend on.

UPDATE members
   SET monthly_subscription_numeric = round(money_text_as_numeric(monthly_subscription), 2),
       current_balance_numeric      = round(money_text_as_numeric(current_balance), 2),
       arrears_balance_numeric      = round(money_text_as_numeric(arrears_balance), 2),
       last_month_balance_numeric   = round(money_text_as_numeric(last_month_balance), 2)
 WHERE monthly_subscription IS NOT NULL
    OR current_balance      IS NOT NULL
    OR arrears_balance      IS NOT NULL
    OR last_month_balance   IS NOT NULL;

-- ── Keeping the two sides in step, from here until V49 ────────────────────────────────────────
-- Whichever side a writer changed is copied onto the other. The OLD build writes only the text
-- columns and the NEW build only the numeric ones, so on any one write at most one side of each pair
-- has changed, and the trigger never has to choose between them.
--
--   text changed    -> numeric := the text, parsed and rounded (NULL if it will not parse, which the
--                      V49 gate above then counts, so an old build's '174,00' cannot be lost either)
--   numeric changed -> text := the numeric, rendered ('174.00'), so a rolled-back old build reads
--                      what the new build wrote
--
-- On INSERT there is no "before", so a side the writer left NULL is filled from the side it set.

CREATE FUNCTION members_money_keep_in_step() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.monthly_subscription_numeric IS NULL AND NEW.monthly_subscription IS NOT NULL THEN
            NEW.monthly_subscription_numeric := round(money_text_as_numeric(NEW.monthly_subscription), 2);
        ELSIF NEW.monthly_subscription IS NULL AND NEW.monthly_subscription_numeric IS NOT NULL THEN
            NEW.monthly_subscription := NEW.monthly_subscription_numeric::TEXT;
        END IF;

        IF NEW.current_balance_numeric IS NULL AND NEW.current_balance IS NOT NULL THEN
            NEW.current_balance_numeric := round(money_text_as_numeric(NEW.current_balance), 2);
        ELSIF NEW.current_balance IS NULL AND NEW.current_balance_numeric IS NOT NULL THEN
            NEW.current_balance := NEW.current_balance_numeric::TEXT;
        END IF;

        IF NEW.arrears_balance_numeric IS NULL AND NEW.arrears_balance IS NOT NULL THEN
            NEW.arrears_balance_numeric := round(money_text_as_numeric(NEW.arrears_balance), 2);
        ELSIF NEW.arrears_balance IS NULL AND NEW.arrears_balance_numeric IS NOT NULL THEN
            NEW.arrears_balance := NEW.arrears_balance_numeric::TEXT;
        END IF;

        IF NEW.last_month_balance_numeric IS NULL AND NEW.last_month_balance IS NOT NULL THEN
            NEW.last_month_balance_numeric := round(money_text_as_numeric(NEW.last_month_balance), 2);
        ELSIF NEW.last_month_balance IS NULL AND NEW.last_month_balance_numeric IS NOT NULL THEN
            NEW.last_month_balance := NEW.last_month_balance_numeric::TEXT;
        END IF;
    ELSE
        IF NEW.monthly_subscription IS DISTINCT FROM OLD.monthly_subscription THEN
            NEW.monthly_subscription_numeric := round(money_text_as_numeric(NEW.monthly_subscription), 2);
        ELSIF NEW.monthly_subscription_numeric IS DISTINCT FROM OLD.monthly_subscription_numeric THEN
            NEW.monthly_subscription := NEW.monthly_subscription_numeric::TEXT;
        END IF;

        IF NEW.current_balance IS DISTINCT FROM OLD.current_balance THEN
            NEW.current_balance_numeric := round(money_text_as_numeric(NEW.current_balance), 2);
        ELSIF NEW.current_balance_numeric IS DISTINCT FROM OLD.current_balance_numeric THEN
            NEW.current_balance := NEW.current_balance_numeric::TEXT;
        END IF;

        IF NEW.arrears_balance IS DISTINCT FROM OLD.arrears_balance THEN
            NEW.arrears_balance_numeric := round(money_text_as_numeric(NEW.arrears_balance), 2);
        ELSIF NEW.arrears_balance_numeric IS DISTINCT FROM OLD.arrears_balance_numeric THEN
            NEW.arrears_balance := NEW.arrears_balance_numeric::TEXT;
        END IF;

        IF NEW.last_month_balance IS DISTINCT FROM OLD.last_month_balance THEN
            NEW.last_month_balance_numeric := round(money_text_as_numeric(NEW.last_month_balance), 2);
        ELSIF NEW.last_month_balance_numeric IS DISTINCT FROM OLD.last_month_balance_numeric THEN
            NEW.last_month_balance := NEW.last_month_balance_numeric::TEXT;
        END IF;
    END IF;
    RETURN NEW;
END
$$;

COMMENT ON FUNCTION members_money_keep_in_step() IS
    'V40 to V49 only: copies whichever of a money column''s text and numeric twin a writer changed '
    'onto the other, so an old build and a new build see the same value. V49 drops it first.';

CREATE TRIGGER members_money_keep_in_step
    BEFORE INSERT OR UPDATE ON members
    FOR EACH ROW EXECUTE FUNCTION members_money_keep_in_step();

-- ── What the deploy log says ──────────────────────────────────────────────────────────────────
-- Read back from the report rather than counted along the way, so the numbers printed are the rows
-- that exist rather than a tally that could disagree with them. Flyway forwards NOTICE to its log,
-- and "how many rows need a human" is the number worth having at deploy time.

DO $report$
DECLARE
    row_count RECORD;
    total     BIGINT;
BEGIN
    FOR row_count IN
        SELECT column_name, outcome, count(*) AS n
          FROM members_money_migration_report
         GROUP BY column_name, outcome
         ORDER BY column_name, outcome
    LOOP
        RAISE NOTICE 'V40 %: % value(s) %', row_count.column_name, row_count.n, row_count.outcome;
    END LOOP;

    SELECT count(*) INTO total FROM members_money_migration_report;
    IF total = 0 THEN
        RAISE NOTICE 'V40: every money value converted verbatim; members_money_migration_report is empty';
    ELSE
        RAISE NOTICE 'V40: % value(s) need a human before V49 can drop the text columns', total;
    END IF;
END
$report$;
