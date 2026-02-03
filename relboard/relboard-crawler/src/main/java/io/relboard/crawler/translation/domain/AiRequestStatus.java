package io.relboard.crawler.translation.domain;

public enum AiRequestStatus {
  REQUESTED,
  SUCCESS,
  FAILED,
  SKIPPED_QUOTA,
  SKIPPED_NO_KEY
}
