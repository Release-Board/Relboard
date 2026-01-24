package io.relboard.crawler.service.implementation;

import io.relboard.crawler.client.RelboardServiceClient;
import io.relboard.crawler.client.dto.TechStackSourceSyncResponse;
import io.relboard.crawler.domain.TechStack;
import io.relboard.crawler.domain.TechStackSource;
import io.relboard.crawler.domain.TechStackSourceType;
import io.relboard.crawler.repository.TechStackRepository;
import io.relboard.crawler.repository.TechStackSourceRepository;
import java.util.List;
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
    techStackSourceRepository
        .findByTechStackAndType(techStack, type)
        .ifPresentOrElse(
            existing -> {
              existing.updateSource(
                  type,
                  source.githubOwner(),
                  source.githubRepo(),
                  source.mavenGroupId(),
                  source.mavenArtifactId());
              techStackSourceRepository.save(existing);
            },
            () ->
                techStackSourceRepository.save(
                    TechStackSource.builder()
                        .techStack(techStack)
                        .type(type)
                        .githubOwner(source.githubOwner())
                        .githubRepo(source.githubRepo())
                        .mavenGroupId(source.mavenGroupId())
                        .mavenArtifactId(source.mavenArtifactId())
                        .build()));
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
