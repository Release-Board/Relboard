package io.relboard.crawler.crawler.application;

import io.relboard.crawler.infra.client.MavenClient;
import io.relboard.crawler.techstack.domain.TechStackSource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MavenCrawlingService {

  private final MavenClient mavenClient;

  public Optional<List<String>> fetchVersions(TechStackSource source) {
    String mavenGroupId = source.getMetadataValue("maven_group_id").orElse(null);
    String mavenArtifactId = source.getMetadataValue("maven_artifact_id").orElse(null);
    if (mavenGroupId == null || mavenArtifactId == null) {
      log.warn(
          "Maven 좌표 정보 부족으로 크롤링 건너뜀 techStack={}",
          source.getTechStack().getName());
      return Optional.empty();
    }
    return mavenClient.fetchVersions(mavenGroupId, mavenArtifactId);
  }
}
