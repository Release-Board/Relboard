package io.relboard.crawler.translation.repository;

import io.relboard.crawler.translation.domain.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {}
