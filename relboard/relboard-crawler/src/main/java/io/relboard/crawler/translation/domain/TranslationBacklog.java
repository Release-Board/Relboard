package io.relboard.crawler.translation.domain;

import io.relboard.crawler.common.BaseEntity;
import io.relboard.crawler.release.domain.ReleaseRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "translation_backlog",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_translation_backlog_release_record",
            columnNames = "release_record_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationBacklog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "release_record_id", nullable = false)
  private ReleaseRecord releaseRecord;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TranslationBacklogStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "source_url")
  private String sourceUrl;

  @Builder
  private TranslationBacklog(
      Long id,
      ReleaseRecord releaseRecord,
      TranslationBacklogStatus status,
      int retryCount,
      String lastError,
      String sourceUrl) {
    this.id = id;
    this.releaseRecord = releaseRecord;
    this.status = status;
    this.retryCount = retryCount;
    this.lastError = lastError;
    this.sourceUrl = sourceUrl;
  }

  public void markProcessing() {
    this.status = TranslationBacklogStatus.PROCESSING;
  }

  public void markDone() {
    this.status = TranslationBacklogStatus.DONE;
    this.lastError = null;
  }

  public void markPending() {
    this.status = TranslationBacklogStatus.PENDING;
  }

  public void recordFailure(String error, int maxRetries) {
    this.retryCount += 1;
    this.lastError = error;
    this.status =
        this.retryCount >= maxRetries
            ? TranslationBacklogStatus.FAILED
            : TranslationBacklogStatus.PENDING;
  }
}
