CREATE TABLE translation_backlog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_record_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    source_url VARCHAR(1024),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_translation_backlog_release_record FOREIGN KEY (release_record_id) REFERENCES release_record (id),
    CONSTRAINT uk_translation_backlog_release_record UNIQUE (release_record_id)
) ENGINE=InnoDB;

CREATE INDEX idx_translation_backlog_status_created_at
    ON translation_backlog (status, created_at);
