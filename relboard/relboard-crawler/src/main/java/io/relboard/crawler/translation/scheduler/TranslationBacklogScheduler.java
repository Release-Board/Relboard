package io.relboard.crawler.translation.scheduler;

import io.relboard.crawler.infra.kafka.KafkaProducer;
import io.relboard.crawler.release.domain.ReleaseRecord;
import io.relboard.crawler.release.event.ReleaseEvent;
import io.relboard.crawler.translation.application.AiTranslationService;
import io.relboard.crawler.translation.domain.BatchTranslationResult;
import io.relboard.crawler.translation.domain.TranslationBacklog;
import io.relboard.crawler.translation.domain.TranslationBacklogStatus;
import io.relboard.crawler.translation.repository.TranslationBacklogRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationBacklogScheduler {

  private final TranslationBacklogRepository translationBacklogRepository;
  private final AiTranslationService aiTranslationService;
  private final KafkaProducer kafkaProducer;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Instant lastBatchRunAt = Instant.EPOCH;

  @Value("${translation.backlog.batch-size:50}")
  private int batchSize;

  @Value("${translation.backlog.min-pending:50}")
  private int minPendingCount;

  @Value("${translation.backlog.min-interval-minutes:60}")
  private int minIntervalMinutes;

  @Scheduled(cron = "${translation.backlog.cron:0 */5 * * * *}")
  @Transactional
  public void run() {
    if (!running.compareAndSet(false, true)) {
      log.info("번역 백로그 스케줄러가 이미 실행 중이라 건너뜀");
      return;
    }

    try {
      long pendingCount =
          translationBacklogRepository.countByStatus(TranslationBacklogStatus.PENDING);
      if (pendingCount == 0) {
        return;
      }

      Instant now = Instant.now();
      boolean ready =
          pendingCount >= minPendingCount
              || Duration.between(lastBatchRunAt, now).toMinutes() >= minIntervalMinutes;
      if (!ready) {
        return;
      }

      List<TranslationBacklog> batch =
          translationBacklogRepository.findByStatusOrderByCreatedAtAsc(
              TranslationBacklogStatus.PENDING, PageRequest.of(0, batchSize));

      if (batch.isEmpty()) {
        return;
      }

      BatchTranslationResult result = aiTranslationService.translateBatch(batch);
      lastBatchRunAt = now;

      if (result.status() == BatchTranslationResult.Status.SKIPPED_QUOTA
          || result.status() == BatchTranslationResult.Status.SKIPPED_NO_KEY) {
        log.info("번역 백로그 처리 중단 status={}", result.status());
        return;
      }

      if (result.status() != BatchTranslationResult.Status.SUCCESS) {
        handleBatchFailure(batch, result.error());
        return;
      }

      Map<Long, String> translations = result.translations();
      for (TranslationBacklog backlog : batch) {
        String translated = translations.get(backlog.getId());
        if (translated == null) {
          handleBatchFailure(batch, "missing translated content");
          return;
        }
        ReleaseRecord record = backlog.getReleaseRecord();
        publishTranslation(record, backlog.getSourceUrl(), translated);
        backlog.markDone();
      }
    } finally {
      running.set(false);
    }
  }

  private void handleBatchFailure(List<TranslationBacklog> batch, String error) {
    for (TranslationBacklog backlog : batch) {
      backlog.recordFailure(error, 3);
    }
  }

  private void publishTranslation(ReleaseRecord record, String sourceUrl, String contentKo) {
    LocalDateTime publishedAt =
        record.getPublishedAt() != null
            ? LocalDateTime.ofInstant(record.getPublishedAt(), ZoneId.of("Asia/Seoul"))
            : null;
    kafkaProducer.sendReleaseEvent(
        new ReleaseEvent(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            new ReleaseEvent.Payload(
                record.getTechStack().getName(),
                record.getVersion(),
                record.getTitle(),
                record.getContent(),
                contentKo,
                publishedAt,
                sourceUrl,
                List.of())));
  }
}
