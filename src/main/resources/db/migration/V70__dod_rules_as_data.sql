-- ---------------------------------------------------------------------------------------------
-- Debit Order Deductions: Stephne's rules as DATA (her answers of 6 October 2026, DOD-01 to DOD-39,
-- and her "Suspension codes" sheet).
--
-- WHAT THIS ADDS
--   1. SASBO's suspension codes from her sheet, for production as well as the demonstrations: 11, 12,
--      13, 15, 16, 18, 19, 21, 30, 31, 32 and 33. INSERTED WHEN ABSENT, by code: a demonstration
--      database may already hold some of them (the reference-list seeder writes 11, 13 and 19 from the
--      August printouts, and the demonstration seeders add any code they use), and a code's row is
--      never rewritten here. 14, 17 and 20 are not on her sheet and are not invented (Friday item 2).
--   2. The bank's return codes (acb_unpaid_codes, V45) learn three things per code, as data an
--      administrator holding DOD_SETTINGS can change, each with where it came from (each wording is
--      the one the union's letters print, or her sheet's own words where no letter had one):
--        * which of SASBO's suspension codes a return under it is recorded as (her sheet maps
--          SASBO 13 -> bank 6, 15 -> 15, 16 -> 4, 18 -> 2, 19 -> 26, 30/31/32/33 -> 30);
--        * whether it may be re-debited: bank 2 yes ("Code 2 means insufficient funds and allows to
--          redebit", DOD-03), bank 4 never ("cannot redebit because it indicates a payment stopped by
--          the member", DOD-03), bank 30, 31, 32 and 34 never ("All the same - No authority to debit
--          which means we cannot try to redebit", DOD-04). Bank 6, 15, 18 and 26 are on her sheet but
--          she has not said: UNKNOWN (NULL), and an unknown code is never re-debited until it is set
--          (Friday item 9). She never wrote "only code 2": no rule here says so;
--        * PROVISIONAL, with a note saying what is still open, wherever she has not settled the row.
--   3. The flat rate before R174.00: R166.65 from 1 January 2025, PROVISIONAL. SASBO's letter to the
--      banks of 26 November 2025 says the subscription "will increase from R166-65 to R174-00 effective
--      1st January 2026" and that "the December 2025 deductions will remain at R166-65". The START
--      DATE, 1 January 2025, is OUR ASSUMPTION (the increases run from January and the banks are told
--      each November, PAY-24), which is why the row is marked provisional. Payroll reconciliation
--      prices a 2025 catch-up from it.
--   4. Non-working days: a day in acb_holidays is now a public holiday OR a day the bank is closed
--      (DOD-17: "Public holidays and any other day the bank is closed"; DOD-18: we keep the list), and
--      the calendar's one row carries the setting DOD-17 leaves open -- she ticked both "South African
--      public holidays only" and "public holidays and any other day the bank is closed" (Friday item
--      14). It defaults to TRUE, counting bank closures: a closure is only ever on the list because
--      SASBO told us of it, and with none on the list the two readings are the same calendar.
--      2028's statutory holidays are added and the calendar is known until 2028-12-31, so January 2028
--      can be prepared in December 2027.
--   5. The audit trail's run-less kinds gain the writes that now belong to no run: a code changed or
--      added, the calendar's setting, a bank's members moved to a new debit day, unpaid counts captured
--      by hand, a member's ending, reactivation and rejoining, and the demonstration seed's completion.
--      DROPPED AND ADDED AGAIN IN ONE STATEMENT, inside this migration's transaction (V43 is never edited).
--
-- ADDITIVE: new columns with defaults, rows inserted when absent, a CHECK widened. Nothing an older
-- build reads is removed or retyped.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

-- 1. SASBO's suspension codes -------------------------------------------------------------------
-- Names in the list's own upper case. 11, 13 and 19 keep the August printouts' names, so a database
-- the reference-list seeder has already filled and one built here agree; the others are her sheet's.
INSERT INTO suspension_codes (code, name)
SELECT v.code, v.name
FROM (VALUES
          (11, 'NOT ON PAYROLL'),
          (12, 'MATERNITY LEAVE'),
          (13, 'DEBIT RETURNED'),
          (15, 'ACCOUNT CLOSED'),
          (16, 'PAYMENT STOPPED'),
          (18, 'INSUFFICIENT FUNDS'),
          (19, 'INVALID ACCOUNT'),
          (21, 'SICK LEAVE'),
          (30, 'NO AUTHORITY TO DEBIT ACCOUNT'),
          (31, 'NO AUTHORITY TO DEBIT ACCOUNT'),
          (32, 'NO AUTHORITY TO DEBIT ACCOUNT'),
          (33, 'NO AUTHORITY TO DEBIT ACCOUNT')) AS v (code, name)
WHERE NOT EXISTS (SELECT 1 FROM suspension_codes s WHERE s.code = v.code);

-- 2. The bank's return codes --------------------------------------------------------------------
-- `suspension_code` is SASBO's number, as suspension_codes.code. Not a foreign key, deliberately: the
-- reference lists are written and kept by their seeder, which never deletes, and the service refuses a code
-- that is not on record when an administrator sets one (SUSPENSION_CODE_UNKNOWN).
ALTER TABLE acb_unpaid_codes
    ADD COLUMN suspension_code INTEGER,
    ADD COLUMN re_debit_source VARCHAR(255),
    ADD COLUMN provisional     BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN note            VARCHAR(255),
    ADD COLUMN updated_by      VARCHAR(100),
    ADD COLUMN updated_at      TIMESTAMPTZ,
    -- Two administrators editing one code: the second is told, never silently overwritten.
    ADD COLUMN version         INTEGER      NOT NULL DEFAULT 0;

UPDATE acb_unpaid_codes
SET suspension_code = 18, re_debit_allowed = TRUE, re_debit_inferred = FALSE, provisional = FALSE, note = NULL,
    re_debit_source = 'Stephne, DOD-03: "Code 2 means insufficient funds and allows to redebit"',
    source = 'The 21 August 2026 report; SASBO''s sheet (DOD-01): SASBO 18 Insufficient Funds against bank code 2'
WHERE code = '2';

UPDATE acb_unpaid_codes
SET suspension_code = 16, re_debit_allowed = FALSE, re_debit_inferred = FALSE, provisional = FALSE, note = NULL,
    re_debit_source = 'Stephne, DOD-03: "Code 4 - cannot redebit because it indicates a payment stopped by the member"',
    source = 'The 21 August 2026 report; SASBO''s sheet (DOD-01): SASBO 16 Payment stopped against bank code 4'
WHERE code = '4';

-- 30 and 34 were inferred from the bureau list (V45); SASBO has now said what they mean (DOD-04).
UPDATE acb_unpaid_codes
SET reason = 'NO_AUTHORITY_TO_DEBIT', wording = 'No Authority To Debit', inferred = FALSE,
    suspension_code = 30, re_debit_allowed = FALSE, re_debit_inferred = FALSE, provisional = TRUE,
    re_debit_source = 'Stephne, DOD-04: "30/31/32/34 - All the same - No authority to debit which means we cannot try to redebit"',
    note = 'SASBO 30, 31, 32 and 33 all mean No authority to debit account; which one a return under this code records is asked (Friday item 3). Until then 30.',
    source = CASE code
        WHEN '30' THEN 'The 21 August 2026 report; SASBO''s sheet (DOD-01): SASBO 30/31/32/33 against bank code 30'
        ELSE 'The 21 August 2026 report; Stephne, DOD-04: the same as 30' END
WHERE code IN ('30', '34');

INSERT INTO acb_unpaid_codes (code, reason, wording, inferred, source, re_debit_allowed, re_debit_inferred,
                              suspension_code, re_debit_source, provisional, note)
SELECT v.code, v.reason, v.wording, v.inferred, v.source, v.re_debit_allowed, v.re_debit_inferred,
       v.suspension_code, v.re_debit_source, v.provisional, v.note
FROM (VALUES
          ('6', 'ACCOUNT_FROZEN', 'Debit returned', FALSE,
           'SASBO''s sheet (DOD-01): SASBO 13 Debit returned against bank code 6',
           CAST(NULL AS BOOLEAN), TRUE, 13, 'Not said by SASBO (Friday item 9)', TRUE,
           'Whether bank code 6 may be re-debited is not said: it is not re-debited until it is set (Friday item 9).'),
          ('15', 'ACCOUNT_CLOSED', 'Account Closed', FALSE,
           'SASBO''s sheet (DOD-01): SASBO 15 Account Closed against bank code 15',
           CAST(NULL AS BOOLEAN), TRUE, 15, 'Not said by SASBO (Friday item 9)', TRUE,
           'Whether bank code 15 may be re-debited is not said: it is not re-debited until it is set (Friday item 9).'),
          ('18', 'ACCOUNT_HOLDER_DECEASED', 'Deceased', FALSE,
           'SASBO''s sheet (DOD-01): bank code 18 Deceased, with no SASBO code beside it',
           CAST(NULL AS BOOLEAN), TRUE, CAST(NULL AS INTEGER), 'Not said by SASBO (Friday item 9)', TRUE,
           'Which SASBO code a Deceased return records, and whether the membership ends rather than is suspended, is asked (Friday item 1).'),
          ('26', 'INVALID_ACCOUNT_NUMBER', 'Invalid Account Number', FALSE,
           'SASBO''s sheet (DOD-01): SASBO 19 Invalid account against bank code 26',
           CAST(NULL AS BOOLEAN), TRUE, 19, 'Not said by SASBO (Friday item 9)', TRUE,
           'Whether bank code 26 may be re-debited is not said: it is not re-debited until it is set (Friday item 9).'),
          ('31', 'NO_AUTHORITY_TO_DEBIT', 'No Authority To Debit', FALSE,
           'Stephne, DOD-04: the same as 30',
           FALSE, FALSE, 30, 'Stephne, DOD-04: "30/31/32/34 - All the same - No authority to debit which means we cannot try to redebit"', TRUE,
           'SASBO 30, 31, 32 and 33 all mean No authority to debit account; which one a return under this code records is asked (Friday item 3). Until then 30.'),
          ('32', 'NO_AUTHORITY_TO_DEBIT', 'No Authority To Debit', FALSE,
           'Stephne, DOD-04: the same as 30',
           FALSE, FALSE, 30, 'Stephne, DOD-04: "30/31/32/34 - All the same - No authority to debit which means we cannot try to redebit"', TRUE,
           'SASBO 30, 31, 32 and 33 all mean No authority to debit account; which one a return under this code records is asked (Friday item 3). Until then 30.')
     ) AS v (code, reason, wording, inferred, source, re_debit_allowed, re_debit_inferred, suspension_code,
             re_debit_source, provisional, note)
WHERE NOT EXISTS (SELECT 1 FROM acb_unpaid_codes c WHERE c.code = v.code);

-- 3. The rate before R174.00 --------------------------------------------------------------------
ALTER TABLE acb_rates ADD COLUMN provisional BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO acb_rates (effective_from, amount, source, provisional)
SELECT DATE '2025-01-01', 166.65,
       'SASBO''s letter to the banks of 26 Nov 2025: R166-65 until December 2025, R174-00 from 1 Jan 2026. The start, 1 Jan 2025, is assumed',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM acb_rates r WHERE r.effective_from = DATE '2025-01-01');

-- 4. Non-working days ---------------------------------------------------------------------------
ALTER TABLE acb_holidays
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'PUBLIC_HOLIDAY',
    ADD CONSTRAINT acb_holidays_kind_known CHECK (kind IN ('PUBLIC_HOLIDAY', 'BANK_CLOSED'));

ALTER TABLE acb_calendar ADD COLUMN bank_closures_count BOOLEAN NOT NULL DEFAULT TRUE;

-- The Public Holidays Act's schedule for 2028, Good Friday and Family Day (Easter Sunday is 16 April
-- 2028), and the Monday the Act's Sunday rule makes a holiday: Heritage Day falls on a Sunday.
INSERT INTO acb_holidays (holiday_date, name, source, kind)
SELECT v.holiday_date, v.name, 'STATUTORY', 'PUBLIC_HOLIDAY'
FROM (VALUES
          (DATE '2028-01-01', 'New Year''s Day'),
          (DATE '2028-03-21', 'Human Rights Day'),
          (DATE '2028-04-14', 'Good Friday'),
          (DATE '2028-04-17', 'Family Day'),
          (DATE '2028-04-27', 'Freedom Day'),
          (DATE '2028-05-01', 'Workers'' Day'),
          (DATE '2028-06-16', 'Youth Day'),
          (DATE '2028-08-09', 'National Women''s Day'),
          (DATE '2028-09-24', 'Heritage Day'),
          (DATE '2028-09-25', 'Heritage Day, observed'),
          (DATE '2028-12-16', 'Day of Reconciliation'),
          (DATE '2028-12-25', 'Christmas Day'),
          (DATE '2028-12-26', 'Day of Goodwill')) AS v (holiday_date, name)
WHERE NOT EXISTS (SELECT 1 FROM acb_holidays h WHERE h.holiday_date = v.holiday_date);

UPDATE acb_calendar SET known_until = DATE '2028-12-31' WHERE id = 1 AND known_until < DATE '2028-12-31';

-- 5. The audit trail's run-less kinds -----------------------------------------------------------
ALTER TABLE acb_audit_events
    DROP CONSTRAINT acb_audit_events_runless_kinds,
    ADD CONSTRAINT acb_audit_events_runless_kinds CHECK (run_id IS NOT NULL OR kind IN (
        'RATE_ADDED', 'HOLIDAY_ADDED', 'HOLIDAY_REMOVED', 'CALENDAR_EXTENDED',
        'OPENING_POSITION_CAPTURED', 'SEED_COMPLETE',
        'CODE_CHANGED', 'CODE_ADDED', 'CALENDAR_SETTING_CHANGED', 'DEBIT_DAY_MOVED', 'UNPAID_COUNTS_CAPTURED',
        'MEMBER_ENDED', 'MEMBER_REACTIVATED', 'MEMBER_REJOINED', 'DOD_SEED_COMPLETE'));
