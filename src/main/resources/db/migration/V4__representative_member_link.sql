ALTER TABLE representatives ADD COLUMN IF NOT EXISTS member_id INTEGER;
ALTER TABLE representatives ADD CONSTRAINT fk_rep_member
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL;
