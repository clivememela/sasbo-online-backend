-- ---------------------------------------------------------------------------------------------
-- The member's four money columns become NUMERIC(12,2). STEP ONE OF TWO.
--
-- WHY THIS EXISTS
-- `monthly_subscription`, `current_balance`, `arrears_balance` and `last_month_balance` are
-- VARCHAR(255) from V1. Text cannot be summed, compared or ordered as money: '100.00' < '9.00'
-- is true in this database today. The ACB module's whole output is a control total -- a count and
-- a rand value the bureau reconciles against -- and a control total computed over text is not a
-- control total.
--
-- WHY TWO STEPS, AND WHY THIS ONE KEEPS THE TEXT
-- A single ALTER COLUMN ... TYPE cannot be undone on production data that nobody has measured:
-- the seed's 640 values are all clean numeric text, so the seed exercises none of the rule below,
-- and what production holds is unknown until this runs there. So step one is ADDITIVE and
-- REVERSIBLE: each text column is renamed `*_text` and keeps every byte it had, a NUMERIC column
-- takes the original name, and the values are copied across under a stated rule. Step two --
-- V49, a later migration -- drops the `*_text` columns, and only after the counts below have been
-- read on production. Renaming rather than adding a differently-named column is what lets the
-- entity move to its final column names once, here, instead of twice.
--
-- THE RULE, STATED -- nothing is silently nulled
--   NULL                          -> NULL. There was no value; nothing is lost and nothing is
--                                   reported, or every empty column on every member would be a
--                                   report row.
--   '' or whitespace              -> NULL, reported as 'blank'.
--   matches ^-?[0-9]+(\.[0-9]+)?$ -> copied across, rounded to two decimals.
--   anything else                 -> NULL, reported as 'unparseable'. The raw text is kept in the
--                                   `*_text` column AND in the report, so no value is destroyed
--                                   by this migration -- a human decides what '174,00' meant.
--   more than two decimals        -> copied across rounded, reported as 'rounded', because
--                                   rounding is a change to a money value and a change nobody can
--                                   see is the kind this plan exists to avoid.
--
-- 🔑 A comma is NOT read as a decimal separator. '174,00' is reported rather than guessed at:
-- read one way it is 174.00 and the other 17400, and money is not the place to guess. The report
-- names every such row.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE members_money_migration_report (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT      NOT NULL,
    column_name TEXT        NOT NULL,
    raw_value   TEXT,
    outcome     TEXT        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE members_money_migration_report IS
    'Every member money value this migration did not copy across verbatim: blank, unparseable, or '
    'rounded to two decimals. Written by V40 and kept -- V49 refuses to drop the *_text columns '
    'while any row here is still unresolved.';

CREATE INDEX idx_money_migration_report_outcome ON members_money_migration_report (outcome);

DO $migration$
DECLARE
    money_column  TEXT;
    text_column   TEXT;
    numeric_shape CONSTANT TEXT := '^-?[0-9]+(\.[0-9]+)?$';
    reported      BIGINT;
    converted     BIGINT;
BEGIN
    FOREACH money_column IN ARRAY ARRAY['monthly_subscription', 'current_balance',
                                        'arrears_balance', 'last_month_balance']
    LOOP
        text_column := money_column || '_text';

        EXECUTE format('ALTER TABLE members RENAME COLUMN %I TO %I', money_column, text_column);
        EXECUTE format('ALTER TABLE members ADD COLUMN %I NUMERIC(12,2)', money_column);

        -- Reported first, while the text is the only value there is. The CASE guards the cast:
        -- a row is only ever cast to numeric after its own regex arm has matched.
        EXECUTE format($sql$
            INSERT INTO members_money_migration_report (member_id, column_name, raw_value, outcome)
            -- Named for the TEXT column, which is where the raw value still is and what V49 will
            -- drop -- so a reader of this report is pointed at the column they must go and fix.
            SELECT id, %1$L, raw,
                   CASE WHEN raw = '' THEN 'blank'
                        WHEN parsed IS NULL THEN 'unparseable'
                        ELSE 'rounded' END
            FROM (
                SELECT id,
                       btrim(%1$I) AS raw,
                       CASE WHEN btrim(%1$I) ~ %3$L THEN btrim(%1$I)::NUMERIC END AS parsed
                FROM members
                WHERE %1$I IS NOT NULL
            ) candidate
            WHERE parsed IS NULL OR parsed <> round(parsed, 2)
        $sql$, text_column, money_column, numeric_shape);
        GET DIAGNOSTICS reported = ROW_COUNT;

        EXECUTE format($sql$
            UPDATE members
               SET %2$I = round(btrim(%1$I)::NUMERIC, 2)
             WHERE %1$I IS NOT NULL
               AND btrim(%1$I) ~ %3$L
        $sql$, text_column, money_column, numeric_shape);
        GET DIAGNOSTICS converted = ROW_COUNT;

        RAISE NOTICE 'V40 %: % value(s) copied to NUMERIC(12,2), % reported in members_money_migration_report',
            money_column, converted, reported;
    END LOOP;
END
$migration$;
