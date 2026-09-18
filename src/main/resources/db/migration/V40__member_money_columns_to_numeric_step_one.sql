-- ---------------------------------------------------------------------------------------------
-- The member's four money columns become NUMERIC(12,2). STEP ONE OF TWO.
--
-- WHY THIS EXISTS
-- `monthly_subscription`, `current_balance`, `arrears_balance` and `last_month_balance` are
-- VARCHAR(255) from V1. Text cannot be summed, compared or ordered as money: '100.00' < '9.00' is
-- true in this database today. The ACB module's whole output is a control total -- a count and a
-- rand value the bureau reconciles against -- and a control total computed over text is not one.
--
-- WHY TWO STEPS, AND WHY THIS ONE KEEPS THE TEXT
-- A single ALTER COLUMN ... TYPE cannot be undone on production data that nobody has measured: the
-- seed's 640 values are all clean numeric text, so the seed exercises none of the rule below, and
-- what production holds is unknown until this runs there. So step one is ADDITIVE and REVERSIBLE:
-- each text column is renamed `*_text` and keeps every byte it had, a NUMERIC column takes the
-- original name, and the values are copied across under the rule stated once, below, as a function.
-- Step two -- V49, a later migration -- drops the `*_text` columns, and only after the counts have
-- been read on production. Renaming rather than adding a differently-named column is what lets the
-- entity move to its final column names once, here, instead of twice.
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
-- lock taken by the first rename below is held until this file ends.
--
-- THE RULE, STATED ONCE -- and nothing is silently nulled
--   NULL                          -> NULL. There was no value; nothing is lost and nothing is
--                                   reported, or every empty column on every member is a report row.
--   '' or whitespace              -> NULL, reported as 'blank'.
--   matches ^-?[0-9]+(\.[0-9]+)?$ -> copied across, rounded to two decimals.
--   anything else                 -> NULL, reported as 'unparseable'. The raw text is kept in the
--                                   `*_text` column, so this migration destroys nothing -- a human
--                                   decides what '174,00' meant.
--   more than two decimals        -> copied across rounded, reported as 'rounded', because rounding
--                                   changes a money value and a change nobody can see is the kind
--                                   this plan exists to avoid.
--
-- 🔑 A comma is NOT read as a decimal separator. '174,00' is reported rather than guessed at: read
-- one way it is 174.00 and the other 17400, and money is not the place to guess.
-- ---------------------------------------------------------------------------------------------

-- A blocked lock must fail this migration, not hang the boot. Flyway runs at application start on a
-- container whose health check has its own patience; without this, one session holding ACCESS SHARE
-- on `members` stalls the deploy with no error anywhere. Five seconds is the same ceiling the test
-- harness sets on its own server.
SET LOCAL lock_timeout = '5s';

-- The rule above, as code, so the eight places that need it cannot drift from each other. Kept
-- after this migration deliberately: V49 has to decide whether every text value has a numeric twin,
-- and that decision must use THIS parse, not a second copy of it.
-- 🔑 It PARSES; it does not round. The rounding happens at the two call sites that want it, because
-- the report has to be able to ask "did rounding change this value?" -- which is `parsed <>
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
    'NULL for anything else, including comma decimals. It does not round -- callers do. V49 uses it '
    'to decide whether the *_text columns can be dropped.';

-- What this migration could not copy verbatim, kept so a human can decide what each value meant.
--
-- ⚠️ It is a RECORD, not a gate. It cannot tell anyone whether a value has since been fixed -- a row
-- written here stays written. V49 decides whether the `*_text` columns may be dropped by asking the
-- DATA, which is derivable at any moment and self-heals the instant somebody corrects a member:
--
--     SELECT count(*) FROM members
--      WHERE (monthly_subscription_text IS NOT NULL AND round(money_text_as_numeric(monthly_subscription_text), 2) IS DISTINCT FROM monthly_subscription)
--         OR (current_balance_text      IS NOT NULL AND round(money_text_as_numeric(current_balance_text), 2)      IS DISTINCT FROM current_balance)
--         OR (arrears_balance_text      IS NOT NULL AND round(money_text_as_numeric(arrears_balance_text), 2)      IS DISTINCT FROM arrears_balance)
--         OR (last_month_balance_text   IS NOT NULL AND round(money_text_as_numeric(last_month_balance_text), 2)   IS DISTINCT FROM last_month_balance);
--
-- Zero means every text value is represented by its numeric twin and the text is safe to drop.
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
    'two decimals -- named for the *_text column that still holds the raw value. A record for a '
    'human to work from, not a gate: see the migration header for the predicate V49 actually uses.';

-- ── The columns ───────────────────────────────────────────────────────────────────────────────
-- Catalog-only, both of them: RENAME COLUMN touches no rows, and ADD COLUMN with no default has
-- rewritten nothing since PostgreSQL 11.

ALTER TABLE members RENAME COLUMN monthly_subscription TO monthly_subscription_text;
ALTER TABLE members RENAME COLUMN current_balance      TO current_balance_text;
ALTER TABLE members RENAME COLUMN arrears_balance      TO arrears_balance_text;
ALTER TABLE members RENAME COLUMN last_month_balance   TO last_month_balance_text;

ALTER TABLE members
    ADD COLUMN monthly_subscription NUMERIC(12,2),
    ADD COLUMN current_balance      NUMERIC(12,2),
    ADD COLUMN arrears_balance      NUMERIC(12,2),
    ADD COLUMN last_month_balance   NUMERIC(12,2);

-- ── What will not be copied ───────────────────────────────────────────────────────────────────
-- Reported BEFORE the backfill, while the text is the only value there is. One statement per
-- column: each is readable on its own and names the column it is about.

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'monthly_subscription_text', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(monthly_subscription_text)                      AS raw,
               money_text_as_numeric(monthly_subscription_text)      AS parsed
          FROM members
         WHERE monthly_subscription_text IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'current_balance_text', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(current_balance_text)                      AS raw,
               money_text_as_numeric(current_balance_text)      AS parsed
          FROM members
         WHERE current_balance_text IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'arrears_balance_text', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(arrears_balance_text)                      AS raw,
               money_text_as_numeric(arrears_balance_text)      AS parsed
          FROM members
         WHERE arrears_balance_text IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
SELECT id, 'last_month_balance_text', raw,
       CASE WHEN raw = '' THEN 'blank'
            WHEN parsed IS NULL THEN 'unparseable'
            ELSE 'rounded' END
  FROM (SELECT id,
               btrim(last_month_balance_text)                      AS raw,
               money_text_as_numeric(last_month_balance_text)      AS parsed
          FROM members
         WHERE last_month_balance_text IS NOT NULL) candidate
 WHERE parsed IS NULL OR parsed <> round(parsed, 2);

-- ── The backfill ──────────────────────────────────────────────────────────────────────────────
-- ONE pass, so each row is rewritten once. A row with no text in any of the four columns is left
-- alone entirely rather than rewritten to set four NULLs.

UPDATE members
   SET monthly_subscription = round(money_text_as_numeric(monthly_subscription_text), 2),
       current_balance      = round(money_text_as_numeric(current_balance_text), 2),
       arrears_balance      = round(money_text_as_numeric(arrears_balance_text), 2),
       last_month_balance   = round(money_text_as_numeric(last_month_balance_text), 2)
 WHERE monthly_subscription_text IS NOT NULL
    OR current_balance_text      IS NOT NULL
    OR arrears_balance_text      IS NOT NULL
    OR last_month_balance_text   IS NOT NULL;

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
        RAISE NOTICE 'V40: % value(s) need a human before V49 can drop the *_text columns', total;
    END IF;
END
$report$;
