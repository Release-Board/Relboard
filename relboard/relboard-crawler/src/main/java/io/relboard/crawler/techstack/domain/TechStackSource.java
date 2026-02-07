package io.relboard.crawler.techstack.domain;

import io.relboard.crawler.common.BaseEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

  @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TechStackSourceMetadata> metadata = new ArrayList<>();

  @Builder
  private TechStackSource(
      Long id,
      TechStack techStack,
      TechStackSourceType type,
      List<TechStackSourceMetadata> metadata) {
    this.id = id;
    this.techStack = techStack;
    this.type = type;
    if (metadata != null) {
      metadata.forEach(this::addMetadata);
    }
  }

  public Optional<String> getMetadataValue(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return metadata.stream()
        .filter(item -> key.equalsIgnoreCase(item.getKey()))
        .map(TechStackSourceMetadata::getValue)
        .filter(Objects::nonNull)
        .findFirst();
  }

  public boolean hasMetadata(String key) {
    return getMetadataValue(key).isPresent();
  }

  public void updateSource(TechStackSourceType type, List<TechStackSourceMetadata> metadata) {
    this.type = type;
    if (metadata == null) {
      return;
    }
    java.util.Map<String, TechStackSourceMetadata> existingByKey = new java.util.HashMap<>();
    java.util.List<TechStackSourceMetadata> dedupedExisting = new java.util.ArrayList<>();
    for (TechStackSourceMetadata item : this.metadata) {
      String normalized = normalizeKey(item.getKey());
      if (normalized == null) {
        continue;
      }
      TechStackSourceMetadata existing = existingByKey.get(normalized);
      if (existing == null) {
        existingByKey.put(normalized, item);
        dedupedExisting.add(item);
      } else if (existing.getValue() == null && item.getValue() != null) {
        existing.updateValue(item.getValue());
      }
    }
    if (dedupedExisting.size() != this.metadata.size()) {
      this.metadata.clear();
      this.metadata.addAll(dedupedExisting);
    }

    java.util.Set<String> incomingKeys = new java.util.HashSet<>();
    for (TechStackSourceMetadata incoming : metadata) {
      String normalized = normalizeKey(incoming.getKey());
      if (normalized == null) {
        continue;
      }
      incomingKeys.add(normalized);
      TechStackSourceMetadata existing = existingByKey.get(normalized);
      if (existing != null) {
        existing.updateValue(incoming.getValue());
      } else {
        addMetadata(incoming);
      }
    }

    // remove stale metadata not in incoming
    this.metadata.removeIf(
        item -> {
          String normalized = normalizeKey(item.getKey());
          return normalized != null && !incomingKeys.contains(normalized);
        });
  }

  public void addMetadata(TechStackSourceMetadata meta) {
    if (meta == null) {
      return;
    }
    meta.assignSource(this);
    this.metadata.add(meta);
  }

  private static String normalizeKey(String key) {
    if (key == null) {
      return null;
    }
    String trimmed = key.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.toLowerCase();
  }
}
