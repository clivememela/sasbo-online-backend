-- Main Categories
INSERT INTO deletion_codes (id, name, parent_id) VALUES (1, 'Left service', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (2, 'Resigned Society', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (3, 'Enrolled In error', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (4, 'Deceased', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (5, 'Subscription in Arrears', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (6, 'Duplication', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (7, 'Unwanted / Supervisor', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (8, 'Dismissed', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (9, 'Invalid Information', NULL);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (10, 'Blacklisted');

-- Resigned Society Sub-Reasons (Parent ID = 2)
INSERT INTO deletion_codes (id, name, parent_id) VALUES (11, 'Financial', 2);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (12, 'Personal', 2);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (13, 'Undisclosed', 2);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (14, 'Poor Service', 2);
INSERT INTO deletion_codes (id, name, parent_id) VALUES (15, 'Other Union', 2);
