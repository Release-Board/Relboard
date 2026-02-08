-- Reset releases for RSS-based tech stacks in crawler DB
-- Delete children first to satisfy FK constraints
DELETE rt
FROM release_tag rt
JOIN release_record rr ON rr.id = rt.release_id
JOIN tech_stack ts ON ts.id = rr.tech_stack_id
WHERE ts.name IN (
  'mysql',
  'postgresql',
  'langchain',
  'milvus',
  'opentelemetry',
  'claude-context'
);

DELETE tb
FROM translation_backlog tb
JOIN release_record rr ON rr.id = tb.release_record_id
JOIN tech_stack ts ON ts.id = rr.tech_stack_id
WHERE ts.name IN (
  'mysql',
  'postgresql',
  'langchain',
  'milvus',
  'opentelemetry',
  'claude-context'
);

DELETE rr
FROM release_record rr
JOIN tech_stack ts ON ts.id = rr.tech_stack_id
WHERE ts.name IN (
  'mysql',
  'postgresql',
  'langchain',
  'milvus',
  'opentelemetry',
  'claude-context'
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
