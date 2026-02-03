package io.relboard.crawler.translation.application;

import io.relboard.crawler.translation.domain.AiRequestLog;
import io.relboard.crawler.translation.domain.AiRequestStatus;
import io.relboard.crawler.translation.domain.AiRequestType;
import io.relboard.crawler.translation.repository.AiRequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiRequestLogService {

  private final AiRequestLogRepository aiRequestLogRepository;

  @Transactional
  public AiRequestLog create(
      String provider,
      String model,
      AiRequestType requestType,
      int batchSize,
      int inputChars,
      int retryCount) {
    AiRequestLog log =
        AiRequestLog.builder()
            .provider(provider)
            .model(model)
            .requestType(requestType)
            .status(AiRequestStatus.REQUESTED)
            .batchSize(batchSize)
            .inputChars(inputChars)
            .retryCount(retryCount)
            .build();
    return aiRequestLogRepository.save(log);
  }

  @Transactional
  public void complete(
      AiRequestLog log,
      AiRequestStatus status,
      int durationMs,
      int outputChars,
      String errorMessage) {
    if (log == null) {
      return;
    }
    log.markCompleted(status, durationMs, outputChars, errorMessage);
    aiRequestLogRepository.save(log);
  }
}
