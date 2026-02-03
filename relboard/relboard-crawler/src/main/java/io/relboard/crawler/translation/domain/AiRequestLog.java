package io.relboard.crawler.translation.domain;

import io.relboard.crawler.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_request_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequestLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private String provider;

  @Column(nullable = false, length = 100)
  private String model;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 30)
  private AiRequestType requestType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AiRequestStatus status;

  @Column(name = "batch_size", nullable = false)
  private int batchSize;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "input_chars")
  private Integer inputChars;

  @Column(name = "output_chars")
  private Integer outputChars;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Builder
  private AiRequestLog(
      Long id,
      String provider,
      String model,
      AiRequestType requestType,
      AiRequestStatus status,
      int batchSize,
      Integer durationMs,
      Integer inputChars,
      Integer outputChars,
      int retryCount,
      String errorMessage) {
    this.id = id;
    this.provider = provider;
    this.model = model;
    this.requestType = requestType;
    this.status = status;
    this.batchSize = batchSize;
    this.durationMs = durationMs;
    this.inputChars = inputChars;
    this.outputChars = outputChars;
    this.retryCount = retryCount;
    this.errorMessage = errorMessage;
  }

  public void markCompleted(
      AiRequestStatus status, Integer durationMs, Integer outputChars, String errorMessage) {
    this.status = status;
    this.durationMs = durationMs;
    this.outputChars = outputChars;
    this.errorMessage = errorMessage;
  }
}
