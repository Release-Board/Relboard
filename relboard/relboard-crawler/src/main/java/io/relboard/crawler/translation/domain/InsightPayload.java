package io.relboard.crawler.translation.domain;

import java.util.List;

public record InsightPayload(
    String shortSummary,
    List<InsightItem> insights,
    MigrationGuide migrationGuide,
    List<String> technicalKeywords) {

  public record InsightItem(String type, String title, String reason) {}

  public record MigrationGuide(String description, MigrationGuideCode code, String checklist) {}

  public record MigrationGuideCode(String before, String after) {}
}
