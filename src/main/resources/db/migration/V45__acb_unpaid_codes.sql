-- ---------------------------------------------------------------------------------------------
-- ACB, the returns loop: what the bank's unpaid codes mean -- as DATA.
--
-- Standard Bank's returns report prints a NUMBER for each unpaid (2, 4, 30 and 34 on the report of
-- 21 August 2026), never a wording. The union's letters speak in wordings -- "Not Provided For",
-- "Payment Stopped". This table is the map between them, and it is a table, not code, because it
-- will change: SASBO has been asked for Standard Bank's full list (question 39), and the day it
-- arrives it is rows added here, not a deployment.
--
-- 🔑 WHAT IS KNOWN AND WHAT IS NOT, IN THE DATA ITSELF. 2 and 4 are the standard bureau codes whose
-- wordings the union's letters already print. 30 and 34 appeared on the August report with no
-- letter behind them; their wordings are INFERRED from the published bureau list and marked so,
-- and they carry no letter until SASBO says which one applies. A code this table does not know is
-- recorded as OTHER with its raw value -- counted, shown, never dropped.
--
-- 🔑 WHETHER A CODE ALLOWS A RE-DEBIT (Q40, Q44 -- amended in place 2026-09-21, unpushed, F53).
-- SASBO: suspension is for a code that does not allow a re-debit, never a penalty for the count.
-- So each code says so -- yes, no, or NULL for unknown -- and recording an unpaid SUGGESTS the
-- decision from it (present again, suspend, nothing); the administrator's own choice always wins.
-- 2 (Not Provided For) yes and 4 (Payment Stopped) no, as SASBO's answers read; 30 and 34 unknown.
-- Every flag is INFERRED until Standard Bank's own list is in hand (question 39), and marked so.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_unpaid_codes (
    code     VARCHAR(10)  PRIMARY KEY,
    reason   VARCHAR(40)  NOT NULL,
    wording  VARCHAR(100) NOT NULL,
    inferred BOOLEAN      NOT NULL,
    source   VARCHAR(255) NOT NULL,
    re_debit_allowed  BOOLEAN,
    re_debit_inferred BOOLEAN,
    CONSTRAINT acb_unpaid_codes_code_digits CHECK (code ~ '^[0-9]{1,3}$')
);

INSERT INTO acb_unpaid_codes (code, reason, wording, inferred, source, re_debit_allowed, re_debit_inferred) VALUES
    ('2',  'NOT_PROVIDED_FOR',        'Not Provided For',        FALSE,
           'The bureau code seen on the 21 August 2026 report; the wording the union''s re-debit letter prints',
           TRUE, TRUE),
    ('4',  'PAYMENT_STOPPED',         'Payment Stopped',         FALSE,
           'The bureau code seen on the 21 August 2026 report; the wording the union''s return-debit letter prints',
           FALSE, TRUE),
    ('30', 'NO_AUTHORITY_TO_DEBIT',   'No Authority To Debit',   TRUE,
           'Seen on the 21 August 2026 report; wording inferred from the bureau list, unconfirmed (question 39)',
           NULL, TRUE),
    ('34', 'AUTHORISATION_CANCELLED', 'Authorisation Cancelled', TRUE,
           'Seen on the 21 August 2026 report; wording inferred from the bureau list, unconfirmed (question 39)',
           NULL, TRUE);
