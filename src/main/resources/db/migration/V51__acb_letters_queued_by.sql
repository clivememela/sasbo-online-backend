-- ---------------------------------------------------------------------------------------------
-- ACB 03.3, who queued a letter -- the gate its sending passes through.
--
-- A letter is sent only if a NAMED administrator's recording queued it. While the API is anonymous
-- (register B1) every recording is the actor `system`, and a letter an anonymous caller caused is
-- never sent -- not even after sign-in arrives; the seed's are `seed`, and written already sent
-- (F62). A letter queued before this column is NULL, and is not sent either.
--
-- ADDITIVE: a nullable column no older build reads or writes.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE acb_letters ADD COLUMN queued_by VARCHAR(100);
