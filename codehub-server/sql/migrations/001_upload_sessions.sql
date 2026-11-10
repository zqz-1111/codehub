-- 仅用于已部署的实例。请在维护窗口内对备份执行。
-- 添加唯一索引前，预检查询必须返回零行。
-- 如果查到记录，请停止并手动处理历史重复项。本迁移不会删除任何数据。
SELECT upload_id, chunk_index, COUNT(*) AS duplicate_count
FROM upload_parts GROUP BY upload_id, chunk_index HAVING COUNT(*) > 1;

-- 每条 DDL 只执行一次，MySQL 执行 DDL 时会隐式提交。通过 init.sql 初始化的新库无需执行。
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

-- 历史记录缺少可信的归属和分片清单，请用新的 uploadId 重新上传。
-- 不要用不完整的历史记录推断归属或 totalChunks。
