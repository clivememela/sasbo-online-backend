-- ---------------------------------------------------------------------------------------------
-- Month end 02.2 -- the indexes the figures read by. THE RULES DECIDE THEM, never the other way
-- about (MD66, MR-PERF-14).
--
-- WHY THEY EXIST
-- After SASBO's members are migrated, nine records in ten are leavers: every figure but "left"
-- reads the tenth. So:
--
--   * `members_on_record` -- a PARTIAL index over the members of a status that is not of kind
--     *leaving*, carrying the columns the figures group and count them by. Its predicate is the
--     leaving statuses AS 03.2 PUBLISHES THEM (MemberStatuses.KINDS); one test holds the two
--     together, so a status added to the leaving kind and not here reddens;
--   * `members_join_date` -- rule N counts a member who joined in the month whether or not they are
--     still on record, so it cannot be the partial index.
--
-- Rule L reads the month's status changes: `member_status_changes_changed_at` is 03.2's (V55).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE INDEX members_on_record ON members (workplace_id, status, join_date)
    WHERE status NOT IN ('Resigned', 'Retired', 'Terminated');

CREATE INDEX members_join_date ON members (join_date);
