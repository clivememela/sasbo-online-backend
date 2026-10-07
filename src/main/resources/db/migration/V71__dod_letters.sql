-- ---------------------------------------------------------------------------------------------
-- Debit Order Deductions: the letters SASBO's answers add (6 October 2026).
--
--   * Three templates join the three V46 knew: PAYMENT_STOPPED (the bank's 4, never re-debited,
--     DOD-03), NO_AUTHORITY (the bank's 30, 31, 32 and 34, "all the same", DOD-04) and
--     MEMBERSHIP_ENDED -- "the member gets a short letter" when a staff member confirms their ending
--     after the third unpaid (DOD-22, DOD-24). The CHECK is DROPPED AND ADDED AGAIN IN ONE STATEMENT,
--     inside this migration's transaction; V46 is never edited.
--   * A line may now carry TWO letters, never two of one kind: the ending letter hangs on the third
--     unpaid's line, which already has the letter its own unpaid sent. So "one letter per unpaid"
--     becomes "one letter of each template per line" -- and a recorded unpaid still queues at most one
--     letter, since its template is fixed by its reason.
--
-- ADDITIVE in effect: every row V46's constraints admitted is still admitted.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE acb_letters
    DROP CONSTRAINT acb_letters_template_known,
    ADD CONSTRAINT acb_letters_template_known CHECK (template IN
        ('RETURN_DEBIT', 'ACCOUNT_CLOSED', 'RE_DEBIT', 'PAYMENT_STOPPED', 'NO_AUTHORITY', 'MEMBERSHIP_ENDED')),
    DROP CONSTRAINT acb_letters_one_per_unpaid,
    ADD CONSTRAINT acb_letters_one_per_line_and_template UNIQUE (line_id, template);
