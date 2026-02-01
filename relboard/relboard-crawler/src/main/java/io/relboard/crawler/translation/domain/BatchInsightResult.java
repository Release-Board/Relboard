package io.relboard.crawler.translation.domain;

import java.util.Map;

public record BatchInsightResult(Status status, Map<Long, InsightPayload> insights, String error) {

  public enum Status {
    SUCCESS,
    SKIPPED_NO_KEY,
    SKIPPED_QUOTA,
    FAILED
  }

  public static BatchInsightResult success(Map<Long, InsightPayload> insights) {
    return new BatchInsightResult(Status.SUCCESS, insights, null);
  }

  public static BatchInsightResult skippedNoKey() {
    return new BatchInsightResult(Status.SKIPPED_NO_KEY, Map.of(), "api-key-missing");
  }

  public static BatchInsightResult skippedQuota() {
    return new BatchInsightResult(Status.SKIPPED_QUOTA, Map.of(), "quota-exceeded");
  }

  public static BatchInsightResult failed(String error) {
    return new BatchInsightResult(Status.FAILED, Map.of(), error);
  }
}
