-- Delete banks with empty names
DELETE FROM banks WHERE TRIM(name) = '';

-- Delete duplicate banks, keeping the row with the lowest id for each name
DELETE FROM banks
WHERE id NOT IN (
    SELECT MIN(id)
    FROM banks
    GROUP BY LOWER(name)
);
