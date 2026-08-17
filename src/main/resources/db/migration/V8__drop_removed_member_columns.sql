-- ============================================================
-- Remove orphaned columns that were dropped from the Java entity
-- but still exist in the database (Hibernate ddl-auto:update
-- never drops columns).
-- ============================================================

-- ─── Members: History & Remarks fields removed per §5 & §6 ───
-- §6.1  Managers Code          → managers_code
-- §6.2  MEMNET Member (Y/N)    → memnet_member
-- §6.3  Print Label for Postage → print_label_for_postage
-- §6.4  Suspension Code        → suspension_code
-- §6.6  Deduct Subs through ACB → deduct_acb
-- §6.7  Print Membership Cards  → print_membership_cards
-- §6.8  Home Loan Reduced Rate  → home_loan_reduced_rate

ALTER TABLE members DROP COLUMN IF EXISTS managers_code;
ALTER TABLE members DROP COLUMN IF EXISTS memnet_member;
ALTER TABLE members DROP COLUMN IF EXISTS print_label_for_postage;
ALTER TABLE members DROP COLUMN IF EXISTS suspension_code;
ALTER TABLE members DROP COLUMN IF EXISTS deduct_acb;
ALTER TABLE members DROP COLUMN IF EXISTS print_membership_cards;
ALTER TABLE members DROP COLUMN IF EXISTS home_loan_reduced_rate;
