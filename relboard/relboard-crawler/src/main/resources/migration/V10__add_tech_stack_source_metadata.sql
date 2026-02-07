CREATE TABLE tech_stack_source_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    meta_key VARCHAR(100) NOT NULL,
    `value` VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_tech_stack_source_metadata_source FOREIGN KEY (source_id)
      REFERENCES tech_stack_source (id) ON DELETE CASCADE,
    UNIQUE KEY uidx_tech_stack_source_meta (source_id, meta_key)
) ENGINE=InnoDB;

INSERT INTO tech_stack_source_metadata (source_id, meta_key, `value`)
SELECT id, 'github_owner', github_owner
FROM tech_stack_source
WHERE github_owner IS NOT NULL;

INSERT INTO tech_stack_source_metadata (source_id, meta_key, `value`)
SELECT id, 'github_repo', github_repo
FROM tech_stack_source
WHERE github_repo IS NOT NULL;

INSERT INTO tech_stack_source_metadata (source_id, meta_key, `value`)
SELECT id, 'maven_group_id', maven_group_id
FROM tech_stack_source
WHERE maven_group_id IS NOT NULL;

INSERT INTO tech_stack_source_metadata (source_id, meta_key, `value`)
SELECT id, 'maven_artifact_id', maven_artifact_id
FROM tech_stack_source
WHERE maven_artifact_id IS NOT NULL;

ALTER TABLE tech_stack_source
  DROP COLUMN github_owner,
  DROP COLUMN github_repo,
  DROP COLUMN maven_group_id,
  DROP COLUMN maven_artifact_id;
