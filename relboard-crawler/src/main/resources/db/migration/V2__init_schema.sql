-- Schema creation
CREATE TABLE tech_stack (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    latest_version VARCHAR(50),
    category VARCHAR(50),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE tech_stack_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tech_stack_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    github_owner VARCHAR(255),
    github_repo VARCHAR(255),
    maven_group_id VARCHAR(255),
    maven_artifact_id VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_tech_stack_source_stack FOREIGN KEY (tech_stack_id) REFERENCES tech_stack (id)
) ENGINE=InnoDB;

CREATE INDEX idx_tech_stack_source_stack ON tech_stack_source (tech_stack_id);

CREATE TABLE release_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tech_stack_id BIGINT NOT NULL,
    version VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    published_at DATETIME(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_release_record_stack FOREIGN KEY (tech_stack_id) REFERENCES tech_stack (id),
    CONSTRAINT uq_release_record_stack_version UNIQUE (tech_stack_id, version)
) ENGINE=InnoDB;

CREATE INDEX idx_release_record_stack ON release_record (tech_stack_id);

CREATE TABLE release_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_id BIGINT NOT NULL,
    tag_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_release_tag_record FOREIGN KEY (release_id) REFERENCES release_record (id)
) ENGINE=InnoDB;

CREATE INDEX idx_release_tag_record ON release_tag (release_id);

-- Seed data for initial crawl targets
INSERT INTO tech_stack (name, latest_version, category)
VALUES
    ('spring-boot', NULL, 'JAVA'),
    ('spring-framework', NULL, 'JAVA'),
    ('micronaut', NULL, 'JAVA');

INSERT INTO tech_stack_source (tech_stack_id, type, github_owner, github_repo, maven_group_id, maven_artifact_id)
VALUES
    ((SELECT id FROM tech_stack WHERE name = 'spring-boot'), 'MAVEN', 'spring-projects', 'spring-boot', 'org.springframework.boot', 'spring-boot'),
    ((SELECT id FROM tech_stack WHERE name = 'spring-framework'), 'MAVEN', 'spring-projects', 'spring-framework', 'org.springframework', 'spring-core'),
    ((SELECT id FROM tech_stack WHERE name = 'micronaut'), 'MAVEN', 'micronaut-projects', 'micronaut-core', 'io.micronaut', 'micronaut-core');
