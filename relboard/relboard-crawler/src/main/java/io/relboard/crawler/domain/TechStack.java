package io.relboard.crawler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tech_stack")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechStack extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "latest_version")
  private String latestVersion;

  @Column private String category;

  @Column(name = "color_hex")
  private String colorHex;

  @Builder
  private TechStack(Long id, String name, String latestVersion, String category, String colorHex) {
    this.id = id;
    this.name = name;
    this.latestVersion = latestVersion;
    this.category = category;
    this.colorHex = colorHex;
  }

  public void updateLatestVersion(String latestVersion) {
    this.latestVersion = latestVersion;
  }

  public void updateCategory(String category) {
    this.category = category;
  }

  public void updateColorHex(String colorHex) {
    this.colorHex = colorHex;
  }
}
