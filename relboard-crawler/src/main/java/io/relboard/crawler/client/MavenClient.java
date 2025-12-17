package io.relboard.crawler.client;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class MavenClient {

    private final RestClient mavenRestClient;

    public MavenClient(@Qualifier("mavenRestClient") final RestClient mavenRestClient){
        this.mavenRestClient = mavenRestClient;
    }

    public Optional<String> fetchLatestVersion(String groupId, String artifactId) {
        try {
            MavenSearchResponse response = mavenRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/solrsearch/select")
                            .queryParam("q", "g:\"" + groupId + "\" AND a:\"" + artifactId + "\"")
                            .queryParam("rows", 1)
                            .queryParam("wt", "json")
                            .build())
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> log.warn("Maven 조회 오류: status={} url={}", res.getStatusCode(), req.getURI()))
                    .body(MavenSearchResponse.class);

            return response != null && response.response != null
                    ? response.response.docs.stream().findFirst().map(doc -> doc.latestVersion)
                    : Optional.empty();
        } catch (Exception ex) {
            log.error("Maven Central 최신 버전 조회 실패 {}:{}", groupId, artifactId, ex);
            return Optional.empty();
        }
    }

    private record MavenSearchResponse(MavenResponse response) {
    }

    private record MavenResponse(List<MavenDoc> docs) {
    }

    private record MavenDoc(String latestVersion) {
    }
}
