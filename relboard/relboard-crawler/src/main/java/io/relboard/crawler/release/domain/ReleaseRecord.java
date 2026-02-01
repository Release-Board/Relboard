package io.relboard.crawler.release.domain;

import io.relboard.crawler.common.BaseEntity;
import io.relboard.crawler.techstack.domain.TechStack;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "release_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReleaseRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tech_stack_id")
  private TechStack techStack;

  @Column(nullable = false)
  private String version;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "content_ko", columnDefinition = "LONGTEXT")
  private String contentKo;

  @Column(name = "short_summary", length = 500)
  private String shortSummary;

  @Column(name = "insights", columnDefinition = "JSON")
  private String insights;

  @Column(name = "migration_guide", columnDefinition = "JSON")
  private String migrationGuide;

  @Column(name = "technical_keywords", columnDefinition = "JSON")
  private String technicalKeywords;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Builder
  private ReleaseRecord(
      Long id,
      TechStack techStack,
      String version,
      String title,
      String content,
      String contentKo,
      String shortSummary,
      String insights,
      String migrationGuide,
      String technicalKeywords,
      Instant publishedAt) {
    this.id = id;
    this.techStack = techStack;
    this.version = version;
    this.title = title;
    this.content = content;
    this.contentKo = contentKo;
    this.shortSummary = shortSummary;
    this.insights = insights;
    this.migrationGuide = migrationGuide;
    this.technicalKeywords = technicalKeywords;
    this.publishedAt = publishedAt;
  }

  public void applyTranslation(String contentKo) {
    this.contentKo = contentKo;
  }

  public void applyInsights(
      String shortSummary, String insights, String migrationGuide, String technicalKeywords) {
    this.shortSummary = shortSummary;
    this.insights = insights;
    this.migrationGuide = migrationGuide;
    this.technicalKeywords = technicalKeywords;
  }
}
