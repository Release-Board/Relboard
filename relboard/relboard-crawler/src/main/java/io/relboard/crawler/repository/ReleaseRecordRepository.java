package io.relboard.crawler.repository;

import io.relboard.crawler.domain.ReleaseRecord;
import io.relboard.crawler.domain.TechStack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRecordRepository extends JpaRepository<ReleaseRecord, Long> {

  boolean existsByTechStackAndVersion(TechStack techStack, String version);
}
