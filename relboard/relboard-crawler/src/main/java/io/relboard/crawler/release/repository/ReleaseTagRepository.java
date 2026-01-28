package io.relboard.crawler.release.repository;

import io.relboard.crawler.release.domain.ReleaseTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseTagRepository extends JpaRepository<ReleaseTag, Long> {}
