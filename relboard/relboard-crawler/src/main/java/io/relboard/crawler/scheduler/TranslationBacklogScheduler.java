package io.relboard.crawler.scheduler;

import io.relboard.crawler.domain.ReleaseRecord;
import io.relboard.crawler.domain.TranslationBacklog;
import io.relboard.crawler.domain.TranslationBacklogStatus;
import io.relboard.crawler.event.ReleaseEvent;
import io.relboard.crawler.repository.TranslationBacklogRepository;
import io.relboard.crawler.service.implementation.AiTranslationService;
import io.relboard.crawler.service.implementation.KafkaProducerService;
import io.relboard.crawler.service.implementation.TranslationResult;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
  private final KafkaProducerService kafkaProducerService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  @Value("${translation.backlog.batch-size:20}")
  private int batchSize;

  @Scheduled(cron = "${translation.backlog.cron:0 */5 * * * *}")
  @Transactional
  public void run() {
    if (!running.compareAndSet(false, true)) {
      log.info("번역 백로그 스케줄러가 이미 실행 중이라 건너뜀");
      return;
    }

    try {
      List<TranslationBacklog> backlogItems = translationBacklogRepository
          .findByStatusInOrderByCreatedAtAsc(
              List.of(TranslationBacklogStatus.PENDING, TranslationBacklogStatus.FAILED),
              PageRequest.of(0, batchSize));

      if (backlogItems.isEmpty()) {
        return;
      }

      for (TranslationBacklog backlog : backlogItems) {
        backlog.markProcessing();

        ReleaseRecord record = backlog.getReleaseRecord();
        TranslationResult result = aiTranslationService.translateWithStatus(record.getContent());

        if (result.status() == TranslationResult.Status.SUCCESS) {
          publishTranslation(record, backlog.getSourceUrl(), result.content());
          backlog.markDone();
          continue;
        }

        if (result.status() == TranslationResult.Status.SKIPPED_QUOTA
            || result.status() == TranslationResult.Status.SKIPPED_NO_KEY) {
          backlog.markPending();
          log.info("번역 백로그 처리 중단 status={}", result.status());
          break;
        }

        if (result.status() == TranslationResult.Status.SKIPPED_EMPTY) {
          backlog.markFailed(result.error());
          continue;
        }

        backlog.markFailed(result.error());
      }
    } finally {
      running.set(false);
    }
  }

  private void publishTranslation(ReleaseRecord record, String sourceUrl, String contentKo) {
    LocalDateTime publishedAt = record.getPublishedAt() != null
        ? LocalDateTime.ofInstant(record.getPublishedAt(), ZoneId.of("Asia/Seoul"))
        : null;
    kafkaProducerService.sendReleaseEvent(
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
