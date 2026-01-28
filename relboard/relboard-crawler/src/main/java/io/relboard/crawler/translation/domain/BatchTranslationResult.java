package io.relboard.crawler.translation.domain;

import java.util.Map;

public record BatchTranslationResult(Status status, Map<Long, String> translations, String error) {

  public enum Status {
    SUCCESS,
    SKIPPED_NO_KEY,
    SKIPPED_QUOTA,
    FAILED
  }

  public static BatchTranslationResult success(Map<Long, String> translations) {
    return new BatchTranslationResult(Status.SUCCESS, translations, null);
  }

  public static BatchTranslationResult skippedNoKey() {
    return new BatchTranslationResult(Status.SKIPPED_NO_KEY, Map.of(), "api-key-missing");
  }

  public static BatchTranslationResult skippedQuota() {
    return new BatchTranslationResult(Status.SKIPPED_QUOTA, Map.of(), "quota-exceeded");
  }

  public static BatchTranslationResult failed(String error) {
    return new BatchTranslationResult(Status.FAILED, Map.of(), error);
  }
}
