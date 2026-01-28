package io.relboard.crawler.translation.repository;

import io.relboard.crawler.translation.domain.TranslationBacklog;
import io.relboard.crawler.translation.domain.TranslationBacklogStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranslationBacklogRepository extends JpaRepository<TranslationBacklog, Long> {

  boolean existsByReleaseRecordId(Long releaseRecordId);

  long countByStatus(TranslationBacklogStatus status);

  @EntityGraph(attributePaths = {"releaseRecord", "releaseRecord.techStack"})
  List<TranslationBacklog> findByStatusOrderByCreatedAtAsc(
      TranslationBacklogStatus status,
      Pageable pageable);

}
