CREATE TABLE ai_request_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    model VARCHAR(100) NOT NULL,
    request_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    batch_size INT NOT NULL,
    duration_ms INT DEFAULT 0,
    input_chars INT DEFAULT 0,
    output_chars INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_ai_request_log_created_at ON ai_request_log (created_at);
CREATE INDEX idx_ai_request_log_status ON ai_request_log (status);
