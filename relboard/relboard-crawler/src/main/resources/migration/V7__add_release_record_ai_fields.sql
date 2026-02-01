ALTER TABLE release_record
  ADD COLUMN content_ko LONGTEXT NULL,
  ADD COLUMN short_summary VARCHAR(500) NULL,
  ADD COLUMN insights JSON NULL,
  ADD COLUMN migration_guide JSON NULL,
  ADD COLUMN technical_keywords JSON NULL;
