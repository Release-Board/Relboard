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
  private final AiTranslationService aiTranslationService;
  private final ReleaseParser releaseParser = new ReleaseParser();

  @Async("crawlerExecutor")
  @Transactional
  @Override
  public void process(Long sourceId) {
    String techStackName = "sourceId=" + sourceId;
    try {
      TechStackSource source = techStackSourceRepository
          .findById(sourceId)
          .orElseThrow(
              () -> new IllegalArgumentException("TechStackSource not found: " + sourceId));

      techStackName = source.getTechStack().getName();
      log.info("크롤링 시작 techStack={}", techStackName);

      if (!source.hasMavenCoordinates() || !source.hasGithubCoordinates()) {
        log.warn("좌표 정보 부족으로 크롤링 건너뜀 techStack={}", techStackName);
        return;
      }

      Optional<List<String>> versionsOpt = mavenClient.fetchVersions(source.getMavenGroupId(),
          source.getMavenArtifactId());
      if (versionsOpt.isEmpty()) {
        log.warn("버전 목록을 찾을 수 없어 크롤링 건너뜀 techStack={}", techStackName);
        return;
      }

      List<String> versions = versionsOpt.get();
      TechStack techStack = source.getTechStack();
      String lastProcessedVersion = null;

      for (String version : versions) {
        if (releaseRecordRepository.existsByTechStackAndVersion(techStack, version)) {
          continue;
        }

        Optional<GithubClient.ReleaseDetails> releaseDetailsOpt = githubClient.fetchReleaseDetails(
            source.getGithubOwner(), source.getGithubRepo(), version);
        if (releaseDetailsOpt.isEmpty()) {
          log.warn("릴리즈 노트를 찾을 수 없어 건너뜀 techStack={} version={} ", techStackName, version);
          continue;
        }

        GithubClient.ReleaseDetails releaseDetails = releaseDetailsOpt.get();
        String translatedContent = aiTranslationService.translateToKorean(releaseDetails.content());
        ReleaseRecord record = releaseRecordRepository.save(
            ReleaseRecord.builder()
                .techStack(techStack)
                .version(version)
                .title(releaseDetails.title() != null ? releaseDetails.title() : version)
                .content(releaseDetails.content())
                .publishedAt(releaseDetails.publishedAt())
                .build());

        Set<ReleaseTagType> tags = releaseParser.extractTags(releaseDetails.content());
        List<ReleaseEvent.Tag> eventTags = tags.stream()
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
                    translatedContent,
                    LocalDateTime.ofInstant(record.getPublishedAt(), ZoneId.of("Asia/Seoul")),
                    releaseDetails.htmlUrl(),
                    eventTags)));

        log.info("릴리즈 크롤링 성공 techStack={} version={} title={}", techStackName, version, record.getTitle());

        lastProcessedVersion = version;
      }

      if (lastProcessedVersion != null) {
        techStack.updateLatestVersion(lastProcessedVersion);
        techStackRepository.save(techStack);
      }

      log.info("크롤링 완료 techStack={} processedUntil={}", techStackName, lastProcessedVersion);
    } catch (Exception ex) {
      log.error("크롤링 실패 techStack={}", techStackName, ex);
    }
  }
}
