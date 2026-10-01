-- ---------------------------------------------------------------------------------------------
-- Month end 04.11 -- a month taken again (MM51, SASBO's answer of 2026-10-01):
--
--   "yes the correction should overwrite the wrong / Figures change daily and we only keep month
--    end records if we need to go back and  for record keeping"
--
-- A month taken again is a NEW TAKE beside the one it replaces, and every read of the month is of
-- its NEWEST take -- the one nothing replaces (MD70). The take it replaced is kept, append-only,
-- and read by nothing but the months' line that the month was taken again. V60 left room for it:
-- month_end_months.replaces_id, and an index that holds the FIRST take of a month to once.
--
--   * take_number -- 1 for a month's first take, one more for each take that replaces it, held to
--     replaces_id by a check. H2, whose schema is built from the entities and has no partial index,
--     holds (month_start, take_number) unique in their place; here the two partial indexes hold it.
--   * month_end_months_replaced_once -- a take is replaced once: two re-takes of the same take, the
--     second loses to the index, never to a look first (MD46).
--   * month_end_months_replaces_its_own_month -- a take replaces only a take of its own month, by a
--     foreign key on (replaces_id, month_start), over the unique (id, month_start) it needs.
--   * month_end_workplaces_one_a_workplace is now (month_id, workplace_key): two takes of a month
--     each hold a row a workplace. Every read selects the take first, then its rows (MR-SCALE-12);
--     month_end_counts is read by its take the same way.
--   * month_end_packs.month_id -- the take a pack was built from: the month's pack is the newest of
--     its newest take. A pack already built names its month's one take.
--
-- No row of an append-only table is written: each column is added with its default, and the
-- index that moves is dropped and built again.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE month_end_months ADD COLUMN take_number INTEGER NOT NULL DEFAULT 1
    CONSTRAINT month_end_months_take_number_follows CHECK ((take_number = 1) = (replaces_id IS NULL) AND take_number >= 1);

CREATE UNIQUE INDEX month_end_months_replaced_once ON month_end_months (replaces_id) WHERE replaces_id IS NOT NULL;

COMMENT ON INDEX month_end_months_replaced_once IS
    'A take is replaced once (MM51, MD70): a second re-take of the same take is refused here.';

ALTER TABLE month_end_months ADD CONSTRAINT month_end_months_id_and_month UNIQUE (id, month_start);

ALTER TABLE month_end_months ADD CONSTRAINT month_end_months_replaces_its_own_month
    FOREIGN KEY (replaces_id, month_start) REFERENCES month_end_months (id, month_start);

DROP INDEX month_end_workplaces_one_a_workplace;

CREATE UNIQUE INDEX month_end_workplaces_one_a_workplace ON month_end_workplaces (month_id, workplace_key);

CREATE INDEX month_end_counts_take ON month_end_counts (month_id, measure);

ALTER TABLE month_end_packs ADD COLUMN month_id BIGINT
    CONSTRAINT month_end_packs_take_fk REFERENCES month_end_months (id);

UPDATE month_end_packs p SET month_id = (SELECT m.id FROM month_end_months m
                                          WHERE m.month_start = p.month_start AND m.replaces_id IS NULL)
WHERE p.month_id IS NULL;
