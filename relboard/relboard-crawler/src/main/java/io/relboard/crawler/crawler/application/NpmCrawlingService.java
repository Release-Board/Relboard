package io.relboard.crawler.crawler.application;

import io.relboard.crawler.infra.client.NpmClient;
import io.relboard.crawler.techstack.domain.TechStackSource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NpmCrawlingService {

  private final NpmClient npmClient;

  public Optional<List<String>> fetchVersions(TechStackSource source) {
    String npmPackageName = source.getMetadataValue("npm_package_name").orElse(null);
    if (npmPackageName == null) {
      log.warn(
          "NPM 패키지 정보 부족으로 크롤링 건너뜀 techStack={}",
          source.getTechStack().getName());
      return Optional.empty();
    }
    return npmClient.fetchVersions(npmPackageName);
  }
}
