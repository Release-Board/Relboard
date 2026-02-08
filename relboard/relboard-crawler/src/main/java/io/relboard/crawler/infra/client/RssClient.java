package io.relboard.crawler.infra.client;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import java.io.StringReader;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssClient {

  private final @Qualifier("rssRestClient") RestClient rssRestClient;

  public List<RssEntry> fetchEntries(String feedUrl, int limit) {
    if (feedUrl == null || feedUrl.isBlank()) {
      return List.of();
    }
    try {
      String xml = rssRestClient.get().uri(feedUrl).retrieve().body(String.class);
      if (xml == null || xml.isBlank()) {
        return List.of();
      }
      SyndFeedInput input = new SyndFeedInput();
      SyndFeed feed = input.build(new StringReader(xml));
      List<SyndEntry> entries =
          Optional.ofNullable(feed.getEntries()).orElse(Collections.emptyList());
      return entries.stream().limit(limit).map(this::toEntry).toList();
    } catch (Exception ex) {
      log.warn("RSS fetch failed url={}", feedUrl, ex);
      return List.of();
    }
  }

  private RssEntry toEntry(SyndEntry entry) {
    String title = entry.getTitle();
    String version = normalizeVersion(title);
    String link = entry.getLink();
    String content = resolveContent(entry);
    Instant publishedAt = resolvePublishedAt(entry);
    return new RssEntry(version, title, content, publishedAt, link);
  }

  private String resolveContent(SyndEntry entry) {
    if (entry.getContents() != null && !entry.getContents().isEmpty()) {
      return entry.getContents().stream()
          .map(SyndContent::getValue)
          .filter(value -> value != null && !value.isBlank())
          .reduce((left, right) -> left + "\n\n" + right)
          .orElse(null);
    }
    if (entry.getDescription() != null) {
      return entry.getDescription().getValue();
    }
    return null;
  }

  private Instant resolvePublishedAt(SyndEntry entry) {
    Date published = entry.getPublishedDate();
    if (published == null) {
      published = entry.getUpdatedDate();
    }
    return published != null ? published.toInstant() : null;
  }

  private String normalizeVersion(String title) {
    if (title == null) {
      return null;
    }
    String trimmed = title.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String normalized = trimmed;
    if (normalized.toLowerCase().startsWith("release ")) {
      normalized = normalized.substring(8).trim();
    }
    if (normalized.startsWith("v") && normalized.length() > 1) {
      char next = normalized.charAt(1);
      if (Character.isDigit(next)) {
        normalized = normalized.substring(1);
      }
    }
    return normalized.isBlank() ? null : normalized;
  }

  public record RssEntry(
      String version, String title, String content, Instant publishedAt, String link) {}
}
