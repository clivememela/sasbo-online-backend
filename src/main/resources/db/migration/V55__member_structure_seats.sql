-- ---------------------------------------------------------------------------------------------
-- V55 -- the SASBO structure answers that follow a "Yes": when a shop steward was elected, and a
-- BEC member's committee, recruiter answer, structures represented and executive position.
--
-- WHY IT EXISTS
-- SASBO, through the owner, 2026-09-30: "Shop Steward (Yes) > Select Election Date. BEC Member
-- (Yes) > Select Branch Committee. We need those functionalities back" -- and the tree they say the
-- screen had: under BEC Member, the committee, BEC Recruiter, BEC Structure Representative (INC /
-- IC, NCF / SJC, VP, President, MANCOM, Resolutions Committee), BEC Executive (Chairperson, Deputy
-- Chairperson, Secretary, Deputy Secretary, Treasurer) and the COSATU structure.
--
-- Capture asked all of these until 2026-09-10 and stored none -- they had no column (CM-05).
-- SASBO's spec then moved the structures to Edit Member, "because a newly captured member cannot
-- sit on a structure" (2.4), and on 2026-09-17 the record was cut to the questions that had a
-- column: Shop Steward?, BEC Member? and COSATU. Each of the rest has a column now:
--
--   shop_steward_election_date  when the member was elected shop steward. NOT election_date:
--                               that column belongs to the representative model, and
--                               MemberServiceImpl.resolveStructureAndRole clears it on every save
--                               of a member who is not flagged a representative.
--   bec_committee_id            the committee the member SITS ON, one of SASBO's committees (V53).
--                               A seat is an answer the member holds. It is not the committee
--                               their workplace belongs to, which SASBO's reports reach through
--                               the workplace (month end's rule G).
--   is_bec_recruiter            BEC Recruiter?, yes/no.
--   is_bec_structure_rep        BEC Structure Representative?, yes/no; and bec_structures, the
--   bec_structures              structures represented, comma-separated in SASBO's spelling -- the
--                               shape V19 gave cosatu_positions.
--   is_bec_executive            BEC Executive?, yes/no; and bec_executive_position, the one
--   bec_executive_position      position held.
--
-- The COSATU structure needs nothing new: V19's cosatu_positions and V29's is_cosatu_local /
-- is_cosatu_regional hold it.
--
-- All nullable: NULL is "never answered", which every existing member is. The rules -- what each
-- Yes needs, what a No clears -- are MemberValidator.validateSeats / validateBecRoles and
-- MemberServiceImpl, where a legacy "Yes" with nothing beside it can still be saved.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE members
    ADD COLUMN shop_steward_election_date DATE,
    ADD COLUMN bec_committee_id           BIGINT CONSTRAINT members_bec_committee_fk REFERENCES committees (id),
    ADD COLUMN is_bec_recruiter           BOOLEAN,
    ADD COLUMN is_bec_structure_rep       BOOLEAN,
    ADD COLUMN bec_structures             VARCHAR(255),
    ADD COLUMN is_bec_executive           BOOLEAN,
    ADD COLUMN bec_executive_position     VARCHAR(40);

-- Month end counts BEC seats per committee; a foreign key is not indexed by PostgreSQL on its own.
CREATE INDEX members_bec_committee_idx ON members (bec_committee_id);
