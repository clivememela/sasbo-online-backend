-- Drop old foreign key pointing from members to representatives table
ALTER TABLE public.members DROP CONSTRAINT IF EXISTS fko50vehm7g7hgw5t9ukg8i8ms1;

-- Add new column fields to support representative tracking inline inside members table
ALTER TABLE public.members
    ADD COLUMN IF NOT EXISTS is_representative BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS role_id INT4,
    ADD COLUMN IF NOT EXISTS structure_id INT4,
    ADD COLUMN IF NOT EXISTS election_date DATE;

-- Update existing records to link directly member-to-member for recruitment tracking
-- This handles the rule: "When a Representative recruits a Member, the Member is linked to that Representative"
ALTER TABLE public.members
    ADD CONSTRAINT fk_member_recruiter_self
        FOREIGN KEY (recruitment_rep) REFERENCES public.members(id);

-- Link representative userRoles and structures straight to the profile columns
ALTER TABLE public.members
    ADD CONSTRAINT fk_member_rep_role FOREIGN KEY (role_id) REFERENCES public.representative_roles(id),
    ADD CONSTRAINT fk_member_union_structure FOREIGN KEY (structure_id) REFERENCES public.union_structures(id);

-- Clean up the old redundant columns if necessary or keep them for backward compatibility.
-- Business rule enforcement check: Ensure self-recruitment loops don't accidentally happen
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
