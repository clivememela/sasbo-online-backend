-- ---------------------------------------------------------------------------------------------
-- The October build, stream bugs (RC15) -- the index the Recruiters screen's figures read by.
--
-- WHY IT EXISTS
-- The screen used to load every member and count them in the browser. The server counts them now,
-- per recruitment channel, with the banks and branches beside each count (one grouped statement),
-- and lists a channel's members a page at a time. Both read members by `recruitment_channel_id`,
-- which no index carried (V23 added the column and its foreign key, and Postgres indexes neither).
--
-- `bank_name` and `branch` ride along so the grouped statement is answered from the index alone:
-- it reads only those three columns. The leading column serves a channel's list and the clearing
-- of a removed channel (`UPDATE members SET recruitment_channel_id = NULL WHERE ...`).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE INDEX members_recruitment_channel ON members (recruitment_channel_id, bank_name, branch);
