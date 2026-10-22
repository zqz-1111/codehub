-- Existing installations only. Run against a backup, during a maintenance window.
-- Preflight MUST return zero rows before adding the unique index.
-- If rows exist, stop and resolve legacy duplicates manually; this migration deletes no data.
SELECT upload_id, chunk_index, COUNT(*) AS duplicate_count
FROM upload_parts GROUP BY upload_id, chunk_index HAVING COUNT(*) > 1;

-- Execute each DDL once; MySQL DDL implicitly commits. Do not rerun on a fresh init.sql database.
ALTER TABLE upload_parts ADD COLUMN size_bytes BIGINT NULL AFTER object_key;
ALTER TABLE upload_parts ADD UNIQUE KEY uk_upload_chunk (upload_id, chunk_index);
CREATE TABLE upload_sessions (
    upload_id VARCHAR(64) PRIMARY KEY,
    repo_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
    merged_file_id BIGINT,
    merged_path VARCHAR(500),
    merged_file_name VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy rows have no trustworthy owner/manifest. Use a new uploadId to re-upload.
-- Do not synthesize ownership or totalChunks from incomplete legacy rows.
