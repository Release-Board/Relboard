-- Reset releases for RSS-based tech stacks in crawler DB
DELETE FROM release_record
WHERE tech_stack_id IN (
  SELECT id FROM tech_stack
  WHERE name IN (
    'mysql',
    'postgresql',
    'langchain',
    'milvus',
    'opentelemetry',
    'claude-context'
  )
);

-- Reset latest version
UPDATE tech_stack
SET latest_version = NULL
WHERE name IN (
  'mysql',
  'postgresql',
  'langchain',
  'milvus',
  'opentelemetry',
  'claude-context'
);
