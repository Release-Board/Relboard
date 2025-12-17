package io.relboard.crawler.repository;

import io.relboard.crawler.domain.ReleaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRecordRepository extends JpaRepository<ReleaseRecord, Long> {}
