-- ---------------------------------------------------------------------------------------------
-- The member register, a page at a time (register row A10) -- the indexes its reads use.
--
-- WHY THEY EXIST
-- The register was GET /api/members: every member, every field, in one answer. SASBO has about
-- 65,600 members today, and after the legacy import nine records in ten are leavers (V58's
-- header): the register is read by the page now (POST /api/members/register/search), and every
-- page must cost the same at 100,000 rows as at 300. So each way the register is read has an
-- index that answers it without reading the table in sequence -- proved by the plan, on 120,000
-- generated members, in MemberRegisterPlanIT.
--
--   * THE ORDERS a page is read in: by surname (the default), by membership number, by bank, by
--     branch and by status. Each ends with the id, so two pages never share a member or skip one,
--     and the bank, branch and status orders lead with their column -- so each is ALSO the index
--     of that filter, read in surname order, and of its count.
--   * THE SEARCH BOX matches the START of a surname, a first name, a membership number or an
--     employee number, in any case: `lower(...) LIKE 'term%'`. This database sorts in en_ZA, and a
--     btree in a language's collation cannot answer LIKE, so these four are text_pattern_ops --
--     which in turn cannot answer ORDER BY, which is why the orders above are indexes of their own.
--   * AN IDENTITY NUMBER is matched whole, or by its last four -- the four every masked display
--     prints (`****1234`). One index of the last four answers both: the whole number is checked
--     on the few rows it finds. Upper-cased, because a passport number is held as typed.
--   * "MORE CRITERIA": an account or a cellphone number by its last four (or whole, likewise),
--     and the start of an e-mail address in any case.
--   * THE FIGURE STRIP's "Updated today" reads the members changed since midnight.
--   * A CALLER LIMITED TO A REGION OR AN AREA reads the members of its workplaces: their count,
--     their statuses and their branches are read from one index over the workplace, the status
--     and the branch, without the table -- a region is a sixth of the register, too much of it to
--     fetch row by row and too little to read all of it. (V57's members_workplace stays: it is
--     month end's, and named by its tests.)
--
-- Title, race, identity type and initials are not indexed: each is shared by a large part of the
-- register, where reading the table in order IS the plan PostgreSQL chooses, and where a page is
-- found quickly by walking an order and filtering. Their counts are bounded by the read's own
-- timeout (MemberRegisterRepository).
--
-- Built inside Flyway's transaction (not CONCURRENTLY): they take a SHARE lock -- reads go on,
-- writes wait -- for as long as each build takes, which is milliseconds on a demonstration
-- database and seconds on SASBO's. Applied before the legacy import, they are built on rows that
-- are not there yet.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

-- The orders, each also the index of a filter on its first column.
CREATE INDEX members_register_surname ON members (lower(surname), lower(first_names), id);
CREATE INDEX members_register_number ON members (membership_number, id);
CREATE INDEX members_register_bank ON members (bank_name, lower(surname), lower(first_names), id);
CREATE INDEX members_register_branch ON members (branch, lower(surname), lower(first_names), id);
CREATE INDEX members_register_status ON members (status, lower(surname), lower(first_names), id);

-- The search box: the start of a name or a number, in any case.
CREATE INDEX members_search_surname ON members (lower(surname) text_pattern_ops);
CREATE INDEX members_search_first_names ON members (lower(first_names) text_pattern_ops);
CREATE INDEX members_search_number ON members (lower(membership_number) text_pattern_ops);
CREATE INDEX members_search_employee ON members (lower(employee_number) text_pattern_ops);

-- An identity number, whole or by its last four.
CREATE INDEX members_search_identity ON members (upper(right(identity_number, 4)));

-- "More criteria".
CREATE INDEX members_search_account ON members (right(bank_account_number, 4));
CREATE INDEX members_search_phone ON members (right(phone, 4));
CREATE INDEX members_search_personal_email ON members (lower(personal_email) text_pattern_ops);
CREATE INDEX members_search_work_email ON members (lower(work_email) text_pattern_ops);

-- The figure strip's "Updated today".
CREATE INDEX members_updated_at ON members (updated_at);

-- A caller's region or area: the members of its workplaces, counted by status and by branch.
CREATE INDEX members_register_scope ON members (workplace_id, status, branch);
