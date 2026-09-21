-- ---------------------------------------------------------------------------------------------
-- ACB 00.5, the member's account type (Q16, 2026-09-18: "yes definitely").
--
-- WHY IT EXISTS
-- The bureau file prints the type of the account every debit draws on -- one digit, position 47 of
-- each debit line: 1 cheque/current, 2 savings, 3 transmission. SASBO's July 2026 file carried
-- 2,746 · 449 · 19 of them. The member record never held it, so every file refused
-- (FILE_LINES_INCOMPLETE). It was requested of the capture arc on 2026-09-18; that arc has closed,
-- and the owner's word of 2026-09-21 ("implement everything of ACB") brings it here.
--
-- STORED AS THE CAPTURE SCREEN OFFERS IT -- 'Cheque', 'Savings', 'Transmission' -- the way
-- payment_type stores 'Debit Order', so what a person picked is what the record says. The run reads
-- the digit from the enum (AccountType), never from this text.
--
-- ADDITIVE, AND SAFE UNDER A ROLLING DEPLOY: a new nullable column. An older build does not know it
-- and inserts NULL, which the CHECK allows. NULL is every member captured before this migration; the
-- run leaves such a member out as SNAPSHOT_INCOMPLETE, "no account type", on the sheet an
-- administrator reads -- so the gap is named, never guessed.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE members ADD COLUMN account_type VARCHAR(20);

ALTER TABLE members ADD CONSTRAINT members_account_type_known
    CHECK (account_type IS NULL OR account_type IN ('Cheque', 'Savings', 'Transmission'));
