-- Add date_of_birth column to members table.
-- For SA ID holders the backend derives this from the identity_number;
-- for passport holders it is entered manually via a date picker.
ALTER TABLE members ADD COLUMN IF NOT EXISTS date_of_birth DATE;
