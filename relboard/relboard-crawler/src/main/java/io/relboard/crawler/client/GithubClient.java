package io.relboard.crawler.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class GithubClient {

  private final RestClient githubRestClient;

  public GithubClient(@Qualifier("githubRestClient") final RestClient githubRestClient) {
    this.githubRestClient = githubRestClient;
  }

  public Optional<ReleaseDetails> fetchReleaseDetails(String owner, String repo, String version) {
    return Stream.of("v" + version, version)
        .distinct()
        .map(tag -> fetchReleaseByTag(owner, repo, tag))
        .filter(Optional::isPresent)
        .findFirst()
        .flatMap(opt -> opt);
  }

  public record ReleaseDetails(String title, String content, Instant publishedAt, String htmlUrl) {
  }

  private Optional<ReleaseDetails> fetchReleaseByTag(String owner, String repo, String tag) {
    try {
      URI uri = URI.create(
          "https://api.github.com/repos/" + owner + "/" + repo + "/releases/tags/" + tag);
      if (log.isTraceEnabled()) {
        log.trace("GitHub 릴리즈 요청 uri={} tag={}", uri, tag);
      }

      GithubReleaseResponse response = githubRestClient
          .get()
          .uri(uri)
          .retrieve()
          .onStatus(
              status -> status.isError(),
              (req, res) -> log.warn(
                  "GitHub 릴리즈 조회 오류: status={} url={}", res.getStatusCode(), req.getURI()))
          .body(GithubReleaseResponse.class);

      if (response == null) {
        return Optional.empty();
      }

      Instant publishedAt = response.publishedAt() != null ? Instant.parse(response.publishedAt()) : null;
      String titleFallback = response.name() != null
          ? response.name()
          : (response.tagName() != null ? response.tagName() : tag);
      String contentFallback = response.body() != null ? response.body() : "";
      String urlFallback = response.htmlUrl() != null ? response.htmlUrl()
          : ("https://github.com/" + owner + "/" + repo + "/releases/tag/" + tag);

      return Optional.of(new ReleaseDetails(titleFallback, contentFallback, publishedAt, urlFallback));
    } catch (Exception ex) {
      log.error("GitHub 릴리즈 노트 조회 실패 {}/{} tag {}", owner, repo, tag, ex);
      return Optional.empty();
    }
  }

  private record GithubReleaseResponse(
      String name,
      String body,
      @JsonProperty("tag_name") String tagName,
      @JsonProperty("published_at") String publishedAt,
      @JsonProperty("html_url") String htmlUrl) {
  }
}
