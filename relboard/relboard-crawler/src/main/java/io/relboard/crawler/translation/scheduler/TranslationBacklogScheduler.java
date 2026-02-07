package io.relboard.crawler.translation.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.relboard.crawler.infra.kafka.KafkaProducer;
import io.relboard.crawler.release.domain.ReleaseRecord;
import io.relboard.crawler.release.event.ReleaseEvent;
import io.relboard.crawler.release.repository.ReleaseRecordRepository;
import io.relboard.crawler.translation.application.AiTranslationService;
import io.relboard.crawler.translation.domain.BatchInsightResult;
import io.relboard.crawler.translation.domain.BatchTranslationResult;
import io.relboard.crawler.translation.domain.InsightPayload;
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
  private final ReleaseRecordRepository releaseRecordRepository;
  private final AiTranslationService aiTranslationService;
  private final KafkaProducer kafkaProducer;
  private final ObjectMapper objectMapper;
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

      List<TranslationBacklog> translateTargets =
          batch.stream().filter(item -> item.getReleaseRecord().getContentKo() == null).toList();

      if (!translateTargets.isEmpty()) {
        BatchTranslationResult result = aiTranslationService.translateBatch(translateTargets);
        lastBatchRunAt = now;

        if (result.status() == BatchTranslationResult.Status.SKIPPED_QUOTA
            || result.status() == BatchTranslationResult.Status.SKIPPED_NO_KEY) {
          log.info("번역 백로그 처리 중단 status={}", result.status());
          return;
        }

        if (result.status() != BatchTranslationResult.Status.SUCCESS) {
          handleBatchFailure(translateTargets, result.error());
          return;
        }

        Map<Long, String> translations = result.translations();
        for (TranslationBacklog backlog : translateTargets) {
          String translated = translations.get(backlog.getId());
          if (translated == null) {
            backlog.recordFailure("missing translated content", 3);
            continue;
          }
          ReleaseRecord record = backlog.getReleaseRecord();
          record.applyTranslation(translated);
          releaseRecordRepository.save(record);
        }
      }

      List<TranslationBacklog> insightTargets =
          batch.stream().filter(item -> item.getReleaseRecord().getShortSummary() == null).toList();

      if (!insightTargets.isEmpty()) {
        BatchInsightResult insightResult =
            aiTranslationService.extractInsightsBatch(insightTargets);
        lastBatchRunAt = now;

        if (insightResult.status() == BatchInsightResult.Status.SKIPPED_QUOTA
            || insightResult.status() == BatchInsightResult.Status.SKIPPED_NO_KEY) {
          log.info("인사이트 백로그 처리 중단 status={}", insightResult.status());
          return;
        }

        if (insightResult.status() != BatchInsightResult.Status.SUCCESS) {
          handleBatchFailure(insightTargets, insightResult.error());
          return;
        }

        Map<Long, InsightPayload> insightsMap = insightResult.insights();
        for (TranslationBacklog backlog : insightTargets) {
          InsightPayload payload = insightsMap.get(backlog.getId());
          if (payload == null || payload.shortSummary() == null) {
            backlog.recordFailure("missing insight payload", 3);
            continue;
          }
          ReleaseRecord record = backlog.getReleaseRecord();
          record.applyInsights(
              payload.shortSummary(),
              serialize(payload.insights()),
              serialize(payload.migrationGuide()),
              serialize(payload.technicalKeywords()));
          releaseRecordRepository.save(record);
        }
      }

      for (TranslationBacklog backlog : batch) {
        ReleaseRecord record = backlog.getReleaseRecord();
        if (record.getContentKo() == null) {
          backlog.recordFailure("missing translated content", 3);
          continue;
        }
        if (record.getShortSummary() == null) {
          backlog.recordFailure("missing insight payload", 3);
          continue;
        }
        InsightPayload insightPayload = parseInsightPayload(record);
        publishTranslation(record, backlog, insightPayload);
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

  private void publishTranslation(
      ReleaseRecord record, TranslationBacklog backlog, InsightPayload insightPayload) {
    LocalDateTime publishedAt =
        record.getPublishedAt() != null
            ? LocalDateTime.ofInstant(record.getPublishedAt(), ZoneId.of("Asia/Seoul"))
            : null;
    List<ReleaseEvent.Insight> insightItems =
        insightPayload == null || insightPayload.insights() == null
            ? List.of()
            : insightPayload.insights().stream()
                .map(item -> new ReleaseEvent.Insight(item.type(), item.title(), item.reason()))
                .toList();
    ReleaseEvent.MigrationGuide migrationGuide = null;
    if (insightPayload != null && insightPayload.migrationGuide() != null) {
      InsightPayload.MigrationGuide source = insightPayload.migrationGuide();
      ReleaseEvent.MigrationGuideCode code =
          source.code() == null
              ? null
              : new ReleaseEvent.MigrationGuideCode(source.code().before(), source.code().after());
      migrationGuide =
          new ReleaseEvent.MigrationGuide(source.description(), code, source.checklist());
    }
    List<String> keywords =
        insightPayload == null || insightPayload.technicalKeywords() == null
            ? List.of()
            : insightPayload.technicalKeywords();
    kafkaProducer.sendReleaseEvent(
        new ReleaseEvent(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            new ReleaseEvent.Payload(
                record.getTechStack().getName(),
                record.getVersion(),
                record.getTitle(),
                record.getContent(),
                record.getContentKo(),
                insightPayload != null ? insightPayload.shortSummary() : null,
                insightItems,
                migrationGuide,
                keywords,
                publishedAt,
                backlog.getSourceUrl(),
                List.of())));
  }

  private String serialize(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      log.warn("인사이트 직렬화 실패: {}", ex.getMessage());
      return null;
    }
  }

  private InsightPayload parseInsightPayload(ReleaseRecord record) {
    if (record.getShortSummary() == null) {
      return null;
    }
    try {
      List<InsightPayload.InsightItem> insights =
          record.getInsights() == null
              ? List.of()
              : objectMapper.readValue(
                  record.getInsights(), new TypeReference<List<InsightPayload.InsightItem>>() {});
      InsightPayload.MigrationGuide migrationGuide =
          record.getMigrationGuide() == null
              ? null
              : objectMapper.readValue(
                  record.getMigrationGuide(), InsightPayload.MigrationGuide.class);
      List<String> keywords =
          record.getTechnicalKeywords() == null
              ? List.of()
              : objectMapper.readValue(
                  record.getTechnicalKeywords(), new TypeReference<List<String>>() {});
      return new InsightPayload(record.getShortSummary(), insights, migrationGuide, keywords);
    } catch (Exception ex) {
      log.warn("인사이트 파싱 실패 id={} reason={}", record.getId(), ex.getMessage());
      return new InsightPayload(record.getShortSummary(), List.of(), null, List.of());
    }
  }
}
