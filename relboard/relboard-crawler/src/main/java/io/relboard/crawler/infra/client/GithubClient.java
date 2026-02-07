package io.relboard.crawler.infra.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

  public Optional<ReleaseDetails> fetchReleaseDetails(String owner, String repo, String version) {
    for (String tag : buildTagCandidates(version)) {
      Optional<ReleaseDetails> result = fetchReleaseByTag(owner, repo, tag);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  public Optional<List<String>> fetchTags(String owner, String repo, int perPage) {
    int size = Math.max(1, Math.min(100, perPage));
    try {
      URI uri =
          URI.create(
              "https://api.github.com/repos/"
                  + owner
                  + "/"
                  + repo
                  + "/tags?per_page="
                  + size
                  + "&page=1");
      if (log.isTraceEnabled()) {
        log.trace("GitHub 태그 요청 uri={}", uri);
      }

      GithubTagResponse[] response =
          githubRestClient
              .get()
              .uri(uri)
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (req, res) ->
                      log.warn(
                          "GitHub 태그 조회 오류: status={} url={}", res.getStatusCode(), req.getURI()))
              .body(GithubTagResponse[].class);

      if (response == null || response.length == 0) {
        return Optional.empty();
      }

      List<String> tags = new ArrayList<>();
      for (GithubTagResponse item : response) {
        if (item != null && item.name() != null && !item.name().isBlank()) {
          tags.add(item.name().trim());
        }
      }

      return tags.isEmpty() ? Optional.empty() : Optional.of(tags);
    } catch (Exception ex) {
      log.error("GitHub 태그 목록 조회 실패 {}/{}", owner, repo, ex);
      return Optional.empty();
    }
  }

  public record ReleaseDetails(String title, String content, Instant publishedAt, String htmlUrl) {}

  private Optional<ReleaseDetails> fetchReleaseByTag(String owner, String repo, String tag) {
    try {
      URI uri =
          URI.create(
              "https://api.github.com/repos/" + owner + "/" + repo + "/releases/tags/" + tag);
      if (log.isTraceEnabled()) {
        log.trace("GitHub 릴리즈 요청 uri={} tag={}", uri, tag);
      }

      GithubReleaseResponse response =
          githubRestClient
              .get()
              .uri(uri)
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
      String titleFallback =
          response.name() != null
              ? response.name()
              : (response.tagName() != null ? response.tagName() : tag);
      String contentFallback = response.body() != null ? response.body() : "";
      String urlFallback =
          response.htmlUrl() != null
              ? response.htmlUrl()
              : ("https://github.com/" + owner + "/" + repo + "/releases/tag/" + tag);

      return Optional.of(
          new ReleaseDetails(titleFallback, contentFallback, publishedAt, urlFallback));
    } catch (Exception ex) {
      log.error("GitHub 릴리즈 노트 조회 실패 {}/{} tag {}", owner, repo, tag, ex);
      return Optional.empty();
    }
  }

  private List<String> buildTagCandidates(String version) {
    if (version == null) {
      return List.of();
    }
    String normalized = version.trim();
    if (normalized.isEmpty()) {
      return List.of();
    }
    if (normalized.startsWith("refs/tags/")) {
      normalized = normalized.substring("refs/tags/".length());
    }

    LinkedHashSet<String> tags = new LinkedHashSet<>();
    tags.add(normalized);

    if (normalized.startsWith("v") || normalized.startsWith("V")) {
      tags.add(normalized.substring(1));
    } else {
      tags.add("v" + normalized);
    }

    return new ArrayList<>(tags);
  }

  private record GithubReleaseResponse(
      String name,
      String body,
      @JsonProperty("tag_name") String tagName,
      @JsonProperty("published_at") String publishedAt,
      @JsonProperty("html_url") String htmlUrl) {}

  private record GithubTagResponse(String name) {}
}
