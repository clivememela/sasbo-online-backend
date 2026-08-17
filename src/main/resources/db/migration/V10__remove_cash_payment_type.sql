-- ============================================================
-- Remove the legacy "Cash" payment type.
--
-- The system only supports "Debit Order" and "Payroll Deduction"
-- (defined in PaymentType enum). Members who previously had
-- "Cash" are set to NULL so the UI can prompt for a valid type.
-- ============================================================

UPDATE members
SET payment_type = NULL
WHERE payment_type = 'Cash';
