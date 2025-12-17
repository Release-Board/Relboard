package io.relboard.crawler.domain;

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

  @Column(name = "published_at")
  private Instant publishedAt;

  @Builder
  private ReleaseRecord(
      Long id,
      TechStack techStack,
      String version,
      String title,
      String content,
      Instant publishedAt) {
    this.id = id;
    this.techStack = techStack;
    this.version = version;
    this.title = title;
    this.content = content;
    this.publishedAt = publishedAt;
  }
}
