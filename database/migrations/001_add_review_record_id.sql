-- MySQL: run once against an existing local/development schema before schema.sql.
-- New installations already contain this column in schema.sql.
-- This migration preserves all existing cases, runs, results and files.
ALTER TABLE test_case ADD COLUMN review_record_id VARCHAR(36) NULL;
ALTER TABLE test_run ADD COLUMN request_fingerprint VARCHAR(64) NULL;
