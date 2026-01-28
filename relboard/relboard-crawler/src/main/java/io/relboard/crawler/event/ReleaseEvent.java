package io.relboard.crawler.event;

import java.time.LocalDateTime;
import java.util.List;

public record ReleaseEvent(String eventId, LocalDateTime occurredAt, Payload payload) {
  public record Payload(
      String techStackName,
      String version,
      String title,
      String content,
      String contentKo,
      LocalDateTime publishedAt,
      String sourceUrl,
      List<Tag> tags) {}

  public record Tag(String type, String reason) {}
}
