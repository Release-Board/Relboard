package io.relboard.crawler.service.implementation;

import io.relboard.crawler.client.GithubClient;
import io.relboard.crawler.client.MavenClient;
import io.relboard.crawler.domain.ReleaseParser;
import io.relboard.crawler.domain.ReleaseRecord;
import io.relboard.crawler.domain.ReleaseTag;
import io.relboard.crawler.domain.ReleaseTagType;
import io.relboard.crawler.domain.TechStack;
import io.relboard.crawler.domain.TechStackSource;
import io.relboard.crawler.event.ReleaseEvent;
import io.relboard.crawler.repository.ReleaseRecordRepository;
import io.relboard.crawler.repository.ReleaseTagRepository;
import io.relboard.crawler.repository.TechStackRepository;
import io.relboard.crawler.repository.TechStackSourceRepository;
import io.relboard.crawler.service.abstraction.CrawlingService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingServiceImpl implements CrawlingService {

  private final TechStackSourceRepository techStackSourceRepository;
  private final TechStackRepository techStackRepository;
  private final ReleaseRecordRepository releaseRecordRepository;
  private final ReleaseTagRepository releaseTagRepository;
  private final MavenClient mavenClient;
  private final GithubClient githubClient;
  private final KafkaProducerService kafkaProducerService;
  private final ReleaseParser releaseParser = new ReleaseParser();

  @Async("crawlerExecutor")
  @Transactional
  @Override
  public void process(Long sourceId) {
    try {
      log.info("크롤링 시작 sourceId={}", sourceId);

      TechStackSource source =
          techStackSourceRepository
              .findById(sourceId)
              .orElseThrow(
                  () -> new IllegalArgumentException("TechStackSource not found: " + sourceId));

      if (!source.hasMavenCoordinates() || !source.hasGithubCoordinates()) {
        log.warn("좌표 정보 부족으로 크롤링 건너뜀 sourceId={}", sourceId);
        return;
      }

      Optional<List<String>> versionsOpt =
          mavenClient.fetchVersions(source.getMavenGroupId(), source.getMavenArtifactId());
      if (versionsOpt.isEmpty()) {
        log.warn("버전 목록을 찾을 수 없어 크롤링 건너뜀 sourceId={}", sourceId);
        return;
      }

      List<String> versions = versionsOpt.get();
      TechStack techStack = source.getTechStack();
      String lastProcessedVersion = null;

      for (String version : versions) {
        if (releaseRecordRepository.existsByTechStackAndVersion(techStack, version)) {
          continue;
        }

        Optional<GithubClient.ReleaseDetails> releaseDetailsOpt =
            githubClient.fetchReleaseDetails(
                source.getGithubOwner(), source.getGithubRepo(), version);
        if (releaseDetailsOpt.isEmpty()) {
          log.warn("릴리즈 노트를 찾을 수 없어 건너뜀 sourceId={} version={} ", sourceId, version);
          continue;
        }

        GithubClient.ReleaseDetails releaseDetails = releaseDetailsOpt.get();
        ReleaseRecord record =
            releaseRecordRepository.save(
                ReleaseRecord.builder()
                    .techStack(techStack)
                    .version(version)
                    .title(releaseDetails.title() != null ? releaseDetails.title() : version)
                    .content(releaseDetails.content())
                    .publishedAt(releaseDetails.publishedAt())
                    .build());

        Set<ReleaseTagType> tags = releaseParser.extractTags(releaseDetails.content());
        List<ReleaseEvent.Tag> eventTags =
            tags.stream()
                .map(
                    tagType -> {
                      releaseTagRepository.save(
                          ReleaseTag.builder().releaseRecord(record).tagType(tagType).build());
                      return new ReleaseEvent.Tag(tagType.name(), "Auto-extracted");
                    })
                .toList();

        // Kafka 메시지 전송
        kafkaProducerService.sendReleaseEvent(
            new ReleaseEvent(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                new ReleaseEvent.Payload(
                    techStack.getName(),
                    version,
                    record.getTitle(),
                    record.getContent(),
                    LocalDateTime.ofInstant(record.getPublishedAt(), ZoneId.of("Asia/Seoul")),
                    source.getGithubRepo(), // TODO: 실제 URL로 변경 필요 (현재는 레포명 우선 사용)
                    eventTags)));

        lastProcessedVersion = version;
      }

      if (lastProcessedVersion != null) {
        techStack.updateLatestVersion(lastProcessedVersion);
        techStackRepository.save(techStack);
      }

      log.info("크롤링 완료 sourceId={} processedUntil={}", sourceId, lastProcessedVersion);
    } catch (Exception ex) {
      log.error("크롤링 실패 sourceId={}", sourceId, ex);
    }
  }
}
