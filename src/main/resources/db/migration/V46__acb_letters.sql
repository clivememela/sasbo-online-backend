-- ---------------------------------------------------------------------------------------------
-- ACB, the union's letters: one per unpaid, with the state of its sending.
--
-- A LETTER IS RENDERED FROM ITS DATA, NOT STORED. The body is the union's template filled from
-- the unpaid line's snapshot and the member's address, re-rendered whenever it is needed; what is
-- kept is its state, when each step happened, the address it went to, and the hash of the body --
-- so a dispute about what a member was sent has an answer without every letter's text, account
-- number included, sitting in a table for ever.
--
-- 🔑 THE STATE IS WHAT STOPS A LETTER GOING TWICE. QUEUED -> RENDERED -> SENT, or FAILED with its
-- attempts counted. A SENT letter is never sent again -- the lesson of the welcome letter, which a
-- timed-out request once sent twice. A member with no email address leaves the letter QUEUED with
-- the reason NO_EMAIL, shown, until SASBO says whether such a letter is posted or held.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_letters (
    id               BIGSERIAL    PRIMARY KEY,
    line_id          BIGINT       NOT NULL CONSTRAINT acb_letters_line_fk REFERENCES acb_run_lines (id),
    template         VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    status_reason    VARCHAR(40),
    channel          VARCHAR(10)  NOT NULL,
    attempts         INTEGER      NOT NULL DEFAULT 0,
    body_sha256      VARCHAR(64),
    address_snapshot VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL,
    rendered_at      TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    failure_class    VARCHAR(200),
    CONSTRAINT acb_letters_one_per_unpaid UNIQUE (line_id),
    CONSTRAINT acb_letters_template_known CHECK (template IN ('RETURN_DEBIT', 'ACCOUNT_CLOSED', 'RE_DEBIT')),
    CONSTRAINT acb_letters_status_known CHECK (status IN ('QUEUED', 'RENDERED', 'SENT', 'FAILED')),
    CONSTRAINT acb_letters_channel_known CHECK (channel IN ('EMAIL', 'POST')),
    CONSTRAINT acb_letters_sent_once CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);
