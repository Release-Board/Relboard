package io.relboard.crawler.client;

import java.net.URI;
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

    // XML에서 <release>3.4.0</release> 추출용 정규식
    // <release>가 없으면 <latest>를 찾도록 대비
    private static final Pattern VERSION_PATTERN = Pattern.compile("<(?:release|latest)>(.*?)</(?:release|latest)>");

    public Optional<String> fetchLatestVersion(String groupId, String artifactId) {
        // Maven 경로 규칙: 점(.)을 슬래시(/)로 변환
        // 예: org.springframework.boot -> org/springframework/boot
        String groupPath = groupId.replace('.', '/');

        try {
            URI uri = URI.create("https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/maven-metadata.xml");
            if (log.isTraceEnabled()) {
                log.trace("Maven metadata 요청 uri={}", uri);
            }

            String xmlContent = mavenRestClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (xmlContent == null || xmlContent.isBlank()) {
                return Optional.empty();
            }

            // 정규식으로 버전 추출 (가볍고 빠름)
            Matcher matcher = VERSION_PATTERN.matcher(xmlContent);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }

            return Optional.empty();

        } catch (Exception ex) {
            // 404 Not Found 등은 경고 로그만 남기고 빈 값 반환
            log.warn("Maven Metadata 조회 실패 (패키지명 확인 필요): {}/{}", groupId, artifactId);
            return Optional.empty();
        }
    }
}
