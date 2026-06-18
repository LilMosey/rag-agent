CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_ext VARCHAR(32) NOT NULL,
    content_type VARCHAR(255) NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(128) NOT NULL,
    storage_bucket VARCHAR(255) NOT NULL,
    storage_object_key VARCHAR(1024) NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    file_status VARCHAR(64) NOT NULL,
    chunk_strategy VARCHAR(64) NOT NULL DEFAULT 'RECURSIVE',
    chunk_size INT NOT NULL DEFAULT 1000,
    chunk_overlap INT NOT NULL DEFAULT 150,
    parse_error VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_kb_filename (knowledge_base_id, original_filename),
    KEY idx_knowledge_base_id (knowledge_base_id),
    KEY idx_file_status (file_status)
);

CREATE TABLE IF NOT EXISTS knowledge_file_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    section_id VARCHAR(128) NULL,
    parent_section_id VARCHAR(128) NULL,
    chunk_index INT NOT NULL,
    chunk_strategy VARCHAR(64) NOT NULL,
    chunk_size INT NOT NULL,
    chunk_overlap INT NOT NULL,
    title_path VARCHAR(1024) NULL,
    content_preview VARCHAR(512) NULL,
    content_hash VARCHAR(128) NOT NULL,
    content_size INT NOT NULL,
    start_offset INT NULL,
    end_offset INT NULL,
    storage_bucket VARCHAR(255) NOT NULL,
    storage_object_key VARCHAR(1024) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_chunk_file_id (file_id),
    KEY idx_chunk_kb_file (knowledge_base_id, file_id),
    KEY idx_chunk_strategy (chunk_strategy)
);

CREATE TABLE IF NOT EXISTS knowledge_file_index_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    error_message VARCHAR(2048) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_index_task_status (status),
    KEY idx_index_task_file_id (file_id)
);
