package io.relboard.crawler.release.repository;

import io.relboard.crawler.release.domain.ReleaseRecord;
import io.relboard.crawler.techstack.domain.TechStack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRecordRepository extends JpaRepository<ReleaseRecord, Long> {

  boolean existsByTechStackAndVersion(TechStack techStack, String version);
}
