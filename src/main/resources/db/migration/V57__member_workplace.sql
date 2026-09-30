-- ---------------------------------------------------------------------------------------------
-- Month end 01.3 -- the member's workplace: each member tied to one of SASBO's workplaces.
--
-- WHY IT EXISTS
-- Every figure SASBO reads is grouped THROUGH THE WORKPLACE: its table code, and through the
-- table code its employer and group; its area and region; its committee (rule G). V54 put the
-- workplaces on record; this ties a member to one.
--
--   * `workplace_id` is NULLABLE: a member with none is counted, everywhere, under one line --
--     "no workplace on record" -- and a save that names none leaves it as it was (MD58), which is
--     every save from a client that has not been built again yet;
--   * a FOREIGN KEY, but never what refuses a save: the service looks the workplace up first and
--     answers WORKPLACE_UNKNOWN (ME-12). A workplace is never deleted -- it is closed (MR-ROB-24) --
--     so the key never has a row to hold on to that has gone;
--   * INDEXED, for the figures that group members by their workplace (P2, P4).
--
-- `bank_name`, `branch` and `ibt_no` are NOT touched: ACB finds the bank by that text, and job
-- grades and subscriptions are keyed by it (BR21).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE members
    ADD COLUMN workplace_id BIGINT CONSTRAINT members_workplace_fk REFERENCES workplaces (id);

CREATE INDEX members_workplace ON members (workplace_id);
