-- Drop old foreign key if it exists (legacy Hibernate-generated name)
ALTER TABLE public.members DROP CONSTRAINT IF EXISTS fko50vehm7g7hgw5t9ukg8i8ms1;

-- Add columns if not already present (V1 creates them on fresh DB)
ALTER TABLE public.members
    ADD COLUMN IF NOT EXISTS is_representative BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS role_id            INT4,
    ADD COLUMN IF NOT EXISTS structure_id       INT4,
    ADD COLUMN IF NOT EXISTS election_date      DATE;

-- Add constraints only if they don't already exist
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_member_recruiter_self') THEN
        ALTER TABLE public.members
            ADD CONSTRAINT fk_member_recruiter_self FOREIGN KEY (recruitment_rep) REFERENCES public.members(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_member_rep_role') THEN
        ALTER TABLE public.members
            ADD CONSTRAINT fk_member_rep_role FOREIGN KEY (role_id) REFERENCES public.representative_roles(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_member_union_structure') THEN
        ALTER TABLE public.members
            ADD CONSTRAINT fk_member_union_structure FOREIGN KEY (structure_id) REFERENCES public.union_structures(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cannot_recruit_self') THEN
        ALTER TABLE public.members
            ADD CONSTRAINT chk_cannot_recruit_self CHECK (recruitment_rep <> id);
    END IF;
END $$;

-- Delete banks with empty names
DELETE FROM banks WHERE TRIM(name) = '';

-- Delete duplicate banks, keeping the row with the lowest id for each name
DELETE FROM banks
WHERE id NOT IN (
    SELECT MIN(id)
    FROM banks
    GROUP BY LOWER(name)
);
