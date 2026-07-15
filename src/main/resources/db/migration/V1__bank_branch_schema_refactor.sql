-- Rename old table if it exists (no-op on fresh DB)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'bank_branch') THEN
        ALTER TABLE bank_branch RENAME TO bank_branches;
    END IF;
END $$;

-- Create table if it still doesn't exist (fresh DB path)
CREATE TABLE IF NOT EXISTS bank_branches (
    id                  SERIAL PRIMARY KEY,
    branch_code         VARCHAR(50),
    bank_id             INTEGER,
    branch_name         VARCHAR(255),
    complex_or_building VARCHAR(100),
    street_number       VARCHAR(20),
    street_name         VARCHAR(100),
    suburb_id           INTEGER,
    city_id             INTEGER,
    province_id         INTEGER,
    postal_code         VARCHAR(10),
    created_at          TIMESTAMP
);

-- Add columns if missing (idempotent for existing tables)
ALTER TABLE bank_branches
    ADD COLUMN IF NOT EXISTS bank_id             INTEGER,
    ADD COLUMN IF NOT EXISTS branch_name         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS complex_or_building VARCHAR(100),
    ADD COLUMN IF NOT EXISTS street_number       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS street_name         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS suburb_id           INTEGER,
    ADD COLUMN IF NOT EXISTS city_id             INTEGER,
    ADD COLUMN IF NOT EXISTS province_id         INTEGER,
    ADD COLUMN IF NOT EXISTS postal_code         VARCHAR(10),
    ADD COLUMN IF NOT EXISTS created_at          TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'bank_branches' AND column_name = 'branch'
    ) THEN
        ALTER TABLE bank_branches RENAME COLUMN branch TO branch_name;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'bank_branches' AND column_name = 'bank_name'
    ) THEN
        UPDATE bank_branches bb
        SET bank_id = b.id
        FROM banks b
        WHERE bb.bank_id IS NULL AND bb.bank_name = b.name;
    END IF;
END $$;

ALTER TABLE IF EXISTS bank_branches
    DROP CONSTRAINT IF EXISTS uk_bank_branches_bank_name_branch_code,
    DROP CONSTRAINT IF EXISTS bank_branch_bank_name_branch_code_key,
    DROP CONSTRAINT IF EXISTS bank_branches_bank_name_branch_code_key;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_branches_bank') THEN
        ALTER TABLE bank_branches ADD CONSTRAINT fk_bank_branches_bank FOREIGN KEY (bank_id) REFERENCES banks(id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_branches_suburb') THEN
        ALTER TABLE bank_branches ADD CONSTRAINT fk_bank_branches_suburb FOREIGN KEY (suburb_id) REFERENCES suburbs(id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_branches_city') THEN
        ALTER TABLE bank_branches ADD CONSTRAINT fk_bank_branches_city FOREIGN KEY (city_id) REFERENCES cities(id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_branches_province') THEN
        ALTER TABLE bank_branches ADD CONSTRAINT fk_bank_branches_province FOREIGN KEY (province_id) REFERENCES provinces(id);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'bank_branches' AND column_name = 'bank_name'
    ) THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_bank_branches_bank_name_branch_code') THEN
            ALTER TABLE bank_branches ADD CONSTRAINT uk_bank_branches_bank_name_branch_code UNIQUE (bank_name, branch_code);
        END IF;
    END IF;
END $$;
