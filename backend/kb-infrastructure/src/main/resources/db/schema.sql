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
    parse_error VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_kb_filename (knowledge_base_id, original_filename),
    KEY idx_knowledge_base_id (knowledge_base_id),
    KEY idx_file_status (file_status)
);
