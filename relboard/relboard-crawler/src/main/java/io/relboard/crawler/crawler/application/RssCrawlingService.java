package io.relboard.crawler.crawler.application;

import io.relboard.crawler.infra.client.RssClient;
import io.relboard.crawler.techstack.domain.TechStackSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssCrawlingService {

  private final RssClient rssClient;

  public List<RssClient.RssEntry> fetchEntries(TechStackSource source) {
    String rssUrl = source.getMetadataValue("rss_url").orElse(null);
    if (rssUrl == null) {
      log.warn(
          "RSS 주소 정보 부족으로 크롤링 건너뜀 techStack={}",
          source.getTechStack().getName());
      return List.of();
    }
    return rssClient.fetchEntries(rssUrl, 30);
  }
}
