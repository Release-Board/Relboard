package io.relboard.crawler.repository;

import io.relboard.crawler.domain.ReleaseTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseTagRepository extends JpaRepository<ReleaseTag, Long> {
}
