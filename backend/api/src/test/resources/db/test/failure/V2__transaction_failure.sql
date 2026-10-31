CREATE TABLE "${applicationSchema}".migration_probe (id INTEGER PRIMARY KEY);
INSERT INTO "${applicationSchema}".migration_probe (id) VALUES (1);
SELECT 1 / 0;
