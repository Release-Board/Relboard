package io.relboard.crawler.techstack.application;

import io.relboard.crawler.infra.client.RelboardServiceClient;
import io.relboard.crawler.infra.client.dto.TechStackSourceSyncResponse;
import io.relboard.crawler.techstack.domain.TechStack;
import io.relboard.crawler.techstack.domain.TechStackSource;
import io.relboard.crawler.techstack.domain.TechStackSourceMetadata;
import io.relboard.crawler.techstack.domain.TechStackSourceType;
import io.relboard.crawler.techstack.repository.TechStackRepository;
import io.relboard.crawler.techstack.repository.TechStackSourceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TechStackSourceSyncService {

  private final RelboardServiceClient relboardServiceClient;
  private final TechStackRepository techStackRepository;
  private final TechStackSourceRepository techStackSourceRepository;

  @Transactional
  public int syncSources() {
    List<TechStackSourceSyncResponse> sources = relboardServiceClient.fetchTechStackSources();
    if (sources.isEmpty()) {
      return 0;
    }

    for (TechStackSourceSyncResponse source : sources) {
      if (source.techStackName() == null || source.techStackName().isBlank()) {
        continue;
      }
      TechStack techStack = upsertTechStack(source);
      upsertTechStackSource(techStack, source);
    }
    return sources.size();
  }

  private TechStack upsertTechStack(TechStackSourceSyncResponse source) {
    return techStackRepository
        .findByName(source.techStackName())
        .map(
            existing -> {
              if (source.category() != null) {
                existing.updateCategory(source.category());
              }
              if (source.colorHex() != null) {
                existing.updateColorHex(source.colorHex());
              }
              return techStackRepository.save(existing);
            })
        .orElseGet(
            () ->
                techStackRepository.save(
                    TechStack.builder()
                        .name(source.techStackName())
                        .category(source.category())
                        .colorHex(source.colorHex())
                        .build()));
  }

  private void upsertTechStackSource(TechStack techStack, TechStackSourceSyncResponse source) {
    TechStackSourceType type = parseSourceType(source.type());
    if (type == null) {
      return;
    }
    List<TechStackSourceMetadata> metadata = buildMetadata(source);
    techStackSourceRepository
        .findByTechStackAndType(techStack, type)
        .ifPresentOrElse(
            existing -> {
              existing.updateSource(type, metadata);
              techStackSourceRepository.save(existing);
            },
            () ->
                techStackSourceRepository.save(
                    TechStackSource.builder()
                        .techStack(techStack)
                        .type(type)
                        .metadata(metadata)
                        .build()));
  }

  private List<TechStackSourceMetadata> buildMetadata(TechStackSourceSyncResponse source) {
    if (source.metadata() == null || source.metadata().isEmpty()) {
      return List.of();
    }
    Map<String, String> deduped =
        source.metadata().stream()
            .filter(Objects::nonNull)
            .filter(item -> item.key() != null && item.value() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    item -> item.key().trim(),
                    TechStackSourceSyncResponse.TechStackSourceMetadataResponse::value,
                    (existing, replacement) -> replacement));

    List<TechStackSourceMetadata> metadata = new ArrayList<>();
    for (Map.Entry<String, String> entry : deduped.entrySet()) {
      metadata.add(
          TechStackSourceMetadata.builder().key(entry.getKey()).value(entry.getValue()).build());
    }
    return metadata;
  }

  private TechStackSourceType parseSourceType(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      return null;
    }
    try {
      return TechStackSourceType.valueOf(rawType.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn("Unknown tech stack source type received: {}", rawType);
      return null;
    }
  }
}
