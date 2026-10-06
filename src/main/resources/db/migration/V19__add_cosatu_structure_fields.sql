-- ============================================================
-- V19: Add COSATU structure fields to members table
--
-- COSATU is modelled as a structure parallel to BEC:
--   - is_cosatu       : whether the member participates in COSATU (Y/N)
--   - cosatu_local    : the Local the member belongs to (free text)
--   - cosatu_region   : the Region the member belongs to (free text)
--   - cosatu_positions: comma-separated positions held within COSATU
--                       (PEC, CEC, Treasurer, Chairperson, Secretary)
--                       A member may hold multiple positions.
-- ============================================================

ALTER TABLE members ADD COLUMN IF NOT EXISTS is_cosatu VARCHAR(1) DEFAULT 'N';
ALTER TABLE members ADD COLUMN IF NOT EXISTS cosatu_local VARCHAR(255);
ALTER TABLE members ADD COLUMN IF NOT EXISTS cosatu_region VARCHAR(255);
ALTER TABLE members ADD COLUMN IF NOT EXISTS cosatu_positions VARCHAR(500);
