-- ---------------------------------------------------------------------------------------------
-- Month end 04.8 -- Memnet, a mark on the member (MM80, SASBO's answer of 2026-10-01):
--
--   "It's a contact member, it get updated or captured as a mnet member when we get the
--    nomination firm. It can be a new member which we will then capture when we capture a new or
--    when we do changes on current member"
--
--   * members.is_memnet -- set at capture or on an edit, from the nomination form. NULL is not
--     answered, as the other structure answers are (V29).
--   * month_end_workplaces.memnet_members -- the members on record so marked, frozen with the rest
--     of the month's row; 04.8 counts them by area and table code, beside the representatives and
--     the committee members. A month taken before today counts none: the mark did not exist then,
--     and a frozen month is never rewritten (MD19). The column is added with its default, so no
--     row is updated and the table's append-only trigger has nothing to refuse.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE members ADD COLUMN is_memnet BOOLEAN;

ALTER TABLE month_end_workplaces ADD COLUMN memnet_members INTEGER NOT NULL DEFAULT 0
    CONSTRAINT month_end_workplaces_memnet_not_negative CHECK (memnet_members >= 0);
