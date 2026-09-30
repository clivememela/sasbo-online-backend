-- ---------------------------------------------------------------------------------------------
-- V55 -- the two SASBO structure answers that follow a "Yes": when a shop steward was elected,
-- and which Branch Executive Committee a BEC member sits on.
--
-- WHY IT EXISTS
-- SASBO, through the owner, 2026-09-30: "Shop Steward (Yes) > Select Election Date. BEC Member
-- (Yes) > Select Branch Committee. We need those functionalities back." Capture asked both until
-- 2026-09-10 and stored neither -- they had no column (CM-05), which is why they were taken off
-- the screen. Each has one now:
--
--   shop_steward_election_date  when the member was elected shop steward. NOT election_date:
--                               that column belongs to the representative model, and
--                               MemberServiceImpl.resolveStructureAndRole clears it on every save
--                               of a member who is not flagged a representative.
--   bec_committee_id            the committee the member SITS ON, one of SASBO's committees (V53).
--                               A seat is an answer the member holds. It is not the committee
--                               their workplace belongs to, which SASBO's reports reach through
--                               the workplace (month end's rule G).
--
-- Both nullable: NULL is "never answered", which every existing member is. The rules -- required
-- when an answer becomes Yes, cleared when it becomes No -- are MemberValidator.validateSeats and
-- MemberServiceImpl, where a legacy "Yes" with nothing beside it can still be saved.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE members
    ADD COLUMN shop_steward_election_date DATE,
    ADD COLUMN bec_committee_id           BIGINT CONSTRAINT members_bec_committee_fk REFERENCES committees (id);

-- Month end counts BEC seats per committee; a foreign key is not indexed by PostgreSQL on its own.
CREATE INDEX members_bec_committee_idx ON members (bec_committee_id);
