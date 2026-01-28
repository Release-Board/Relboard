package io.relboard.crawler.repository;

import io.relboard.crawler.domain.TranslationBacklog;
import io.relboard.crawler.domain.TranslationBacklogStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranslationBacklogRepository extends JpaRepository<TranslationBacklog, Long> {

  boolean existsByReleaseRecordId(Long releaseRecordId);

  @EntityGraph(attributePaths = {"releaseRecord", "releaseRecord.techStack"})
  List<TranslationBacklog> findByStatusInOrderByCreatedAtAsc(
      List<TranslationBacklogStatus> statuses,
      Pageable pageable);
}
