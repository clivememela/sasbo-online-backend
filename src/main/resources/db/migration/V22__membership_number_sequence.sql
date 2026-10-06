-- ============================================================
-- V22: the membership number sequence
--
-- 🔴 Until 03.4 NOTHING generated a membership number. `Member.membershipNumber`
-- exists, `MemberRepository.existsByMembershipNumber` exists, and NOTHING CALLED
-- IT -- so every member captured through the API was stored with a NULL
-- membership number while the 160 seeded members all have one.
--
-- WHY A SEQUENCE AND NOT `existsByMembershipNumber` (BR2-4, SR-10)
-- The obvious implementation is "find the highest, add one, check it is free".
-- That is a check-then-act race: two captures running at the same moment both
-- read 100160, both find 100161 free, and both take it. The window is small and
-- the consequence is permanent -- there is no DELETE endpoint (register D9), so
-- a duplicate membership number has to be edited into something else by hand,
-- forever.
--
-- A sequence is atomic by construction. `nextval` never returns the same value
-- to two callers, with no lock, no retry and no window.
--
-- ⚠️ IT STARTS AT 100161, NOT AT 1. The seed occupies M-100001..M-100160
-- contiguously, so 100161 is the first free number. Starting anywhere lower
-- would collide with real rows on the very first capture.
--
-- ⚠️ A SEQUENCE IS NOT TRANSACTIONAL, and that is correct here. A rolled-back
-- capture consumes its number and leaves a gap. Gaps are the right trade: a
-- membership number is an identifier, not a count, and the alternative is
-- reintroducing the race to keep the sequence pretty.
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS membership_number_seq
    START WITH 100161
    INCREMENT BY 1
    NO CYCLE;

-- The sequence starts above the seeded range. PostgreSQL-specific setval logic
-- is intentionally omitted here so the migration also runs on H2 test databases;
-- production inserts remain collision-free because new values use nextval().
