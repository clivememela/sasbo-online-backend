-- Drop old foreign key if it exists (legacy Hibernate-generated name)
ALTER TABLE public.members DROP CONSTRAINT IF EXISTS fko50vehm7g7hgw5t9ukg8i8ms1;

-- Add columns individually because H2 does not accept PostgreSQL's
-- comma-separated ADD COLUMN form.
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS is_representative BOOLEAN DEFAULT FALSE;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS role_id INT4;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS structure_id INT4;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS election_date DATE;

-- The surviving V1 schema already supplies the role and structure foreign keys.
-- Add only the constraints that are absent from that baseline.
ALTER TABLE public.members
    ADD CONSTRAINT fk_member_recruiter_self FOREIGN KEY (recruitment_rep) REFERENCES public.members(id);

ALTER TABLE public.members
    ADD CONSTRAINT fk_member_rep_role FOREIGN KEY (role_id) REFERENCES public.representative_roles(id);

ALTER TABLE public.members
    ADD CONSTRAINT fk_member_union_structure FOREIGN KEY (structure_id) REFERENCES public.union_structures(id);

ALTER TABLE public.members
    ADD CONSTRAINT chk_cannot_recruit_self CHECK (recruitment_rep <> id);

-- Delete banks with empty names
DELETE FROM banks WHERE TRIM(name) = '';

-- Delete duplicate banks, keeping the row with the lowest id for each name
DELETE FROM banks
WHERE id NOT IN (
    SELECT MIN(id)
    FROM banks
    GROUP BY LOWER(name)
);
