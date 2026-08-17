-- ============================================================
-- Ensure job_grade column accepts alphanumeric text.
--
-- Different banks use different job grade formats (e.g. "A3",
-- "M12", "Senior"). The JPA entity already declares String
-- jobGrade, but Hibernate's ddl-auto:update never changes
-- column types. If the initial schema created it as numeric,
-- INSERTs of alphanumeric values will fail.
-- ============================================================

ALTER TABLE members ALTER COLUMN job_grade TYPE VARCHAR(255)
    USING job_grade::VARCHAR;
