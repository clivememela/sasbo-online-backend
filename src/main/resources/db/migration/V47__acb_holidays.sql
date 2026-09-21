-- ---------------------------------------------------------------------------------------------
-- ACB, the holiday calendar as DATA (D30 -- SASBO's answer to Q29, 2026-09-21: the bank "also
-- decline[s] if it has an incorrect debit date, like a holiday").
--
-- WHY A TABLE, AND WHY NOW
-- A bureau file with one public holiday in it is declined WHOLE. Under the window rule as SASBO
-- re-decided it (Q33, forward first), a holiday pushes a debit forward onto a day that must itself
-- be free -- so the calendar the rule walks is load-bearing, and "weekends only" is no longer an
-- honest default. A holiday is DATA, never code (D18): South Africa's Public Holidays Act gives a
-- schedule, Easter's two days and a Sunday rule, and then days are declared ad hoc (an election, a
-- day of mourning). A list can be corrected by adding a row; a computation is wrong, silently, the
-- first time that happens.
--
-- THE SUNDAY RULE IS MATERIALISED AS ROWS, NEVER COMPUTED: a holiday on a Sunday makes the Monday
-- one (2026-08-10, 2027-03-22, 2027-12-27 below), and each such Monday is its own row, named.
--
-- KNOWN-UNTIL. The calendar says how far it is complete, and a month beyond it is refused
-- (HOLIDAYS_NOT_KNOWN) rather than prepared on weekends alone -- one row in acb_calendar.
--
-- ONE LIST, THREE READERS. These rows are the list the window fixture's `za-statutory` set
-- publishes and the tests' calendar (AcbCalendars) reads; the test constant that mirrors them is
-- MigrationSeed.ACB_HOLIDAYS, and one integration test asserts this table equals it (F47).
--
-- `source` says who put a day here: STATUTORY (this migration, from the Act) or ADDED (an
-- administrator, once the API knows its callers -- B1). Only an ADDED day may be removed.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_holidays (
    holiday_date DATE         PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    source       VARCHAR(20)  NOT NULL,
    added_by     VARCHAR(100),
    added_at     TIMESTAMPTZ,
    CONSTRAINT acb_holidays_source_known CHECK (source IN ('STATUTORY', 'ADDED')),
    CONSTRAINT acb_holidays_added_says_who CHECK (source = 'STATUTORY' OR (added_by IS NOT NULL AND added_at IS NOT NULL))
);

-- One row: the calendar's name, which every run stores, and the last date it is complete for.
CREATE TABLE acb_calendar (
    id          INTEGER      PRIMARY KEY CONSTRAINT acb_calendar_one_row CHECK (id = 1),
    name        VARCHAR(40)  NOT NULL,
    known_until DATE         NOT NULL
);

INSERT INTO acb_holidays (holiday_date, name, source) VALUES
    (DATE '2026-01-01', 'New Year''s Day',                    'STATUTORY'),
    (DATE '2026-03-21', 'Human Rights Day',                   'STATUTORY'),
    (DATE '2026-04-03', 'Good Friday',                        'STATUTORY'),
    (DATE '2026-04-06', 'Family Day',                         'STATUTORY'),
    (DATE '2026-04-27', 'Freedom Day',                        'STATUTORY'),
    (DATE '2026-05-01', 'Workers'' Day',                      'STATUTORY'),
    (DATE '2026-06-16', 'Youth Day',                          'STATUTORY'),
    (DATE '2026-08-09', 'National Women''s Day',              'STATUTORY'),
    (DATE '2026-08-10', 'National Women''s Day, observed',    'STATUTORY'),
    (DATE '2026-09-24', 'Heritage Day',                       'STATUTORY'),
    (DATE '2026-12-16', 'Day of Reconciliation',              'STATUTORY'),
    (DATE '2026-12-25', 'Christmas Day',                      'STATUTORY'),
    (DATE '2026-12-26', 'Day of Goodwill',                    'STATUTORY'),
    (DATE '2027-01-01', 'New Year''s Day',                    'STATUTORY'),
    (DATE '2027-03-21', 'Human Rights Day',                   'STATUTORY'),
    (DATE '2027-03-22', 'Human Rights Day, observed',         'STATUTORY'),
    (DATE '2027-03-26', 'Good Friday',                        'STATUTORY'),
    (DATE '2027-03-29', 'Family Day',                         'STATUTORY'),
    (DATE '2027-04-27', 'Freedom Day',                        'STATUTORY'),
    (DATE '2027-05-01', 'Workers'' Day',                      'STATUTORY'),
    (DATE '2027-06-16', 'Youth Day',                          'STATUTORY'),
    (DATE '2027-08-09', 'National Women''s Day',              'STATUTORY'),
    (DATE '2027-09-24', 'Heritage Day',                       'STATUTORY'),
    (DATE '2027-12-16', 'Day of Reconciliation',              'STATUTORY'),
    (DATE '2027-12-25', 'Christmas Day',                      'STATUTORY'),
    (DATE '2027-12-26', 'Day of Goodwill',                    'STATUTORY'),
    (DATE '2027-12-27', 'Day of Goodwill, observed',          'STATUTORY');

INSERT INTO acb_calendar (id, name, known_until) VALUES (1, 'za-statutory', DATE '2027-12-31');
