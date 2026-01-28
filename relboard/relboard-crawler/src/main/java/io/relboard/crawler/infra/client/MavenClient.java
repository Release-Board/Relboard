package io.relboard.crawler.infra.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class MavenClient {

  private final RestClient mavenRestClient;

  public MavenClient(@Qualifier("mavenRestClient") final RestClient mavenRestClient) {
    this.mavenRestClient = mavenRestClient;
  }

  // XML에서 <version>...</version> 들을 모두 추출
  private static final Pattern VERSION_PATTERN = Pattern.compile("<version>(.*?)</version>");

  public Optional<List<String>> fetchVersions(String groupId, String artifactId) {
    String groupPath = groupId.replace('.', '/');

    try {
      URI uri =
          URI.create(
              "https://repo1.maven.org/maven2/"
                  + groupPath
                  + "/"
                  + artifactId
                  + "/maven-metadata.xml");
      if (log.isTraceEnabled()) {
        log.trace("Maven metadata 요청 uri={}", uri);
      }

      String xmlContent = mavenRestClient.get().uri(uri).retrieve().body(String.class);

      if (xmlContent == null || xmlContent.isBlank()) {
        return Optional.empty();
      }

      Matcher matcher = VERSION_PATTERN.matcher(xmlContent);
      List<String> versions = new ArrayList<>();
      while (matcher.find()) {
        versions.add(matcher.group(1));
      }

      return versions.isEmpty() ? Optional.empty() : Optional.of(versions);

    } catch (Exception ex) {
      log.warn("Maven Metadata 조회 실패 (패키지명 확인 필요): {}/{}", groupId, artifactId);
      return Optional.empty();
    }
  }

  public Optional<String> fetchLatestVersion(String groupId, String artifactId) {
    return fetchVersions(groupId, artifactId)
        .flatMap(
            list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1)));
  }
}
