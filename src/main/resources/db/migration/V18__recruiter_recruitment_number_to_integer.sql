-- ============================================================
-- V18: recruiters.recruitment_number -> INTEGER
--
-- recruitment_number is the COUNT of members a recruiter / recruitment
-- channel has recruited, so it must be numeric rather than text. V17
-- created the column as VARCHAR (matching the then-String entity field);
-- this converts it to INTEGER.
--
-- All seeded values are numeric strings, so the cast is lossless. The
-- ::text cast keeps this safe whether the column is currently varchar or
-- already integer, and NULL/blank values are preserved as NULL.
-- ============================================================

ALTER TABLE recruiters
    ALTER COLUMN recruitment_number TYPE INTEGER
    USING NULLIF(btrim(recruitment_number::text), '')::INTEGER;
