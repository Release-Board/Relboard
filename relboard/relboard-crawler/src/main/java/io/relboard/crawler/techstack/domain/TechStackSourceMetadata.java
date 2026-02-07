package io.relboard.crawler.techstack.domain;

import io.relboard.crawler.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "tech_stack_source_metadata")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechStackSourceMetadata extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_id")
  private TechStackSource source;

  @Column(name = "meta_key", nullable = false, length = 100)
  private String key;

  @Column(name = "`value`", nullable = false, length = 500)
  private String value;

  @Builder
  private TechStackSourceMetadata(Long id, TechStackSource source, String key, String value) {
    this.id = id;
    this.source = source;
    this.key = key;
    this.value = value;
  }

  public void assignSource(TechStackSource source) {
    this.source = source;
  }

  public void updateValue(String value) {
    this.value = value;
  }
}
