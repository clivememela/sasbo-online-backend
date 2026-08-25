-- Seed data for subscriptions
-- bank_id set to NULL to avoid FK issues during initial seeding

INSERT INTO subscriptions (bank_id, acb_debit_day, subscription_amount, core_banking, status, registration_date)
VALUES (NULL, 15, 151.00, false, 'ACTIVE', current_timestamp);

INSERT INTO subscriptions (bank_id, acb_debit_day, subscription_amount, core_banking, status, registration_date)
VALUES (NULL, 1, 165.50, true, 'ACTIVE', current_timestamp);

INSERT INTO subscriptions (bank_id, acb_debit_day, subscription_amount, core_banking, status, registration_date)
VALUES (NULL, 28, 120.00, false, 'PENDING', current_timestamp);
