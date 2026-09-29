-- ---------------------------------------------------------------------------------------------
-- Month end 01.2 -- SASBO's workplaces: the branches and departments their members work at.
--
-- WHY IT EXISTS
-- A member is counted in a group THROUGH THEIR WORKPLACE: its table code, and through the table
-- code its employer and group; its area and region; its committee (rule G). The branch list of
-- 31 August 2026 prints 5,507 of them, each with the number SASBO knows it by -- its R/A number,
-- the list's ACCNO -- its staff complement, and the code of its province and committee.
--
-- A WORKPLACE IS GIVEN A TABLE CODE ONLY WHERE THE FILES PROVE IT (MD42): its employer's name
-- matches one code, or matches two and one of them has exactly the members of the name's
-- workplaces. The row says which, NAME or SUMS; the 22 the files do not settle have none, and are
-- never given one by likeness of name. `committee_code` is kept exactly as printed: six codes on
-- the list have no committee on the province report, and their workplaces keep the code and no
-- committee. A workplace is never deleted: it is closed, and says since when (MR-ROB-24).
--
-- ADDITIVE: `bank_branches` -- sixty-eight invented rows that capture, the Bank Branch screens and
-- the Location Listing read -- is untouched. Nothing here names a person or counts a member.
-- The rows are written by the seeder, and only where the deployment's switch says SASBO's
-- workplaces may be on record (MD45, MD50, MD62).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE workplaces (
    id               BIGSERIAL    PRIMARY KEY,
    ra_number        INTEGER      NOT NULL CONSTRAINT workplaces_ra_number_unique UNIQUE,
    name             VARCHAR(100) NOT NULL,
    employer_printed VARCHAR(60)  NOT NULL,
    table_code_id    BIGINT       CONSTRAINT workplaces_table_code_fk REFERENCES table_codes (id),
    table_code_by    VARCHAR(10),
    area_id          BIGINT       NOT NULL CONSTRAINT workplaces_area_fk REFERENCES sasbo_areas (id),
    committee_code   VARCHAR(3)   NOT NULL,
    committee_id     BIGINT       CONSTRAINT workplaces_committee_fk REFERENCES committees (id),
    staff_complement INTEGER      NOT NULL,
    cl_code          VARCHAR(12)  NOT NULL,
    closed_on        DATE,
    CONSTRAINT workplaces_table_code_by_known CHECK (table_code_by IS NULL OR table_code_by IN ('NAME', 'SUMS')),
    CONSTRAINT workplaces_table_code_proved CHECK ((table_code_id IS NULL) = (table_code_by IS NULL)),
    CONSTRAINT workplaces_complement_not_negative CHECK (staff_complement >= 0)
);
