package io.relboard.crawler.techstack.domain;

import io.relboard.crawler.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tech_stack_source")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechStackSource extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tech_stack_id")
  private TechStack techStack;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TechStackSourceType type;

  @Column(name = "github_owner")
  private String githubOwner;

  @Column(name = "github_repo")
  private String githubRepo;

  @Column(name = "maven_group_id")
  private String mavenGroupId;

  @Column(name = "maven_artifact_id")
  private String mavenArtifactId;

  @Builder
  private TechStackSource(
      Long id,
      TechStack techStack,
      TechStackSourceType type,
      String githubOwner,
      String githubRepo,
      String mavenGroupId,
      String mavenArtifactId) {
    this.id = id;
    this.techStack = techStack;
    this.type = type;
    this.githubOwner = githubOwner;
    this.githubRepo = githubRepo;
    this.mavenGroupId = mavenGroupId;
    this.mavenArtifactId = mavenArtifactId;
  }

  public boolean hasMavenCoordinates() {
    return mavenGroupId != null && mavenArtifactId != null;
  }

  public boolean hasGithubCoordinates() {
    return githubOwner != null && githubRepo != null;
  }

  public void updateSource(
      TechStackSourceType type,
      String githubOwner,
      String githubRepo,
      String mavenGroupId,
      String mavenArtifactId) {
    this.type = type;
    this.githubOwner = githubOwner;
    this.githubRepo = githubRepo;
    this.mavenGroupId = mavenGroupId;
    this.mavenArtifactId = mavenArtifactId;
  }
}
