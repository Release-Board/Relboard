package io.relboard.crawler.service.implementation;

public record TranslationResult(Status status, String content, String error) {

  public enum Status {
    SUCCESS,
    SKIPPED_NO_KEY,
    SKIPPED_QUOTA,
    SKIPPED_EMPTY,
    FAILED
  }

  public static TranslationResult success(String content) {
    return new TranslationResult(Status.SUCCESS, content, null);
  }

  public static TranslationResult skippedNoKey() {
    return new TranslationResult(Status.SKIPPED_NO_KEY, null, "api-key-missing");
  }

  public static TranslationResult skippedQuota() {
    return new TranslationResult(Status.SKIPPED_QUOTA, null, "quota-exceeded");
  }

  public static TranslationResult skippedEmpty() {
    return new TranslationResult(Status.SKIPPED_EMPTY, null, "empty-content");
  }

  public static TranslationResult failed(String error) {
    return new TranslationResult(Status.FAILED, null, error);
  }
}
