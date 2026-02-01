package io.relboard.crawler.release.event;

import java.time.LocalDateTime;
import java.util.List;

public record ReleaseEvent(String eventId, LocalDateTime occurredAt, Payload payload) {
  public record Payload(
      String techStackName,
      String version,
      String title,
      String content,
      String contentKo,
      String shortSummary,
      List<Insight> insights,
      MigrationGuide migrationGuide,
      List<String> technicalKeywords,
      LocalDateTime publishedAt,
      String sourceUrl,
      List<Tag> tags) {}

  public record Tag(String type, String reason) {}

  public record Insight(String type, String title, String reason) {}

  public record MigrationGuide(String description, MigrationGuideCode code, String checklist) {}

  public record MigrationGuideCode(String before, String after) {}
}
