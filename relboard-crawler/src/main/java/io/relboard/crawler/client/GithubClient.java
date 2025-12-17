package io.relboard.crawler.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.relboard.crawler.domain.ReleaseNote;
import java.time.Instant;
import java.util.Optional;
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

  public Optional<ReleaseNote> fetchReleaseNote(String owner, String repo, String version) {
    try {
      GithubReleaseResponse response =
          githubRestClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/repos/{owner}/{repo}/releases/tags/{tag}")
                          .build(owner, repo, version))
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (req, res) ->
                      log.warn(
                          "GitHub 릴리즈 조회 오류: status={} url={}", res.getStatusCode(), req.getURI()))
              .body(GithubReleaseResponse.class);

      if (response == null) {
        return Optional.empty();
      }

      Instant publishedAt =
          response.publishedAt() != null ? Instant.parse(response.publishedAt()) : null;
      return Optional.of(new ReleaseNote(version, response.name(), response.body(), publishedAt));
    } catch (Exception ex) {
      log.error("GitHub 릴리즈 노트 조회 실패 {}/{} tag {}", owner, repo, version, ex);
      return Optional.empty();
    }
  }

  private record GithubReleaseResponse(
      String name,
      String body,
      @JsonProperty("tag_name") String tagName,
      @JsonProperty("published_at") String publishedAt) {}
}
