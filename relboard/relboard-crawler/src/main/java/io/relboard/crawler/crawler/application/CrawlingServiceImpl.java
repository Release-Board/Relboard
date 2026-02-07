package io.relboard.crawler.crawler.application;

import io.relboard.crawler.infra.client.GithubClient;
import io.relboard.crawler.infra.client.MavenClient;
import io.relboard.crawler.infra.client.NpmClient;
import io.relboard.crawler.infra.kafka.KafkaProducer;
import io.relboard.crawler.release.domain.ReleaseParser;
import io.relboard.crawler.release.domain.ReleaseRecord;
import io.relboard.crawler.release.domain.ReleaseTag;
import io.relboard.crawler.release.domain.ReleaseTagType;
import io.relboard.crawler.release.event.ReleaseEvent;
import io.relboard.crawler.release.repository.ReleaseRecordRepository;
import io.relboard.crawler.release.repository.ReleaseTagRepository;
import io.relboard.crawler.techstack.domain.TechStack;
import io.relboard.crawler.techstack.domain.TechStackSource;
import io.relboard.crawler.techstack.domain.TechStackSourceType;
import io.relboard.crawler.techstack.repository.TechStackRepository;
import io.relboard.crawler.techstack.repository.TechStackSourceRepository;
import io.relboard.crawler.translation.domain.TranslationBacklog;
import io.relboard.crawler.translation.domain.TranslationBacklogStatus;
import io.relboard.crawler.translation.repository.TranslationBacklogRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final NpmClient npmClient;
  private final GithubClient githubClient;
  private final KafkaProducer kafkaProducer;
  private final TranslationBacklogRepository translationBacklogRepository;
  private final ReleaseParser releaseParser = new ReleaseParser();

  @Transactional
  @Override
  public void process(Long sourceId) {
    long processStartNs = System.nanoTime();
    String techStackName = "sourceId=" + sourceId;
    try {
      TechStackSource source =
          techStackSourceRepository
              .findById(sourceId)
              .orElseThrow(
                  () -> new IllegalArgumentException("TechStackSource not found: " + sourceId));

      techStackName = source.getTechStack().getName();
      log.info("크롤링 시작 techStack={}", techStackName);

      String githubOwner = source.getMetadataValue("github_owner").orElse(null);
      String githubRepo = source.getMetadataValue("github_repo").orElse(null);
      String mavenGroupId = source.getMetadataValue("maven_group_id").orElse(null);
      String mavenArtifactId = source.getMetadataValue("maven_artifact_id").orElse(null);
      String npmPackageName = source.getMetadataValue("npm_package_name").orElse(null);

      Optional<List<String>> versionsOpt = Optional.empty();

      if (githubOwner != null && githubRepo != null) {
        versionsOpt = githubClient.fetchTags(githubOwner, githubRepo, 30);
      }

      if (versionsOpt.isEmpty()) {
        if (source.getType() == TechStackSourceType.MAVEN) {
          if (mavenGroupId == null || mavenArtifactId == null) {
            log.warn("Maven 좌표 정보 부족으로 크롤링 건너뜀 techStack={}", techStackName);
            return;
          }
          versionsOpt = mavenClient.fetchVersions(mavenGroupId, mavenArtifactId);
        } else if (source.getType() == TechStackSourceType.NPM) {
          if (npmPackageName == null) {
            log.warn("NPM 패키지 정보 부족으로 크롤링 건너뜀 techStack={}", techStackName);
            return;
          }
          versionsOpt = npmClient.fetchVersions(npmPackageName);
        }
      }
      if (versionsOpt.isEmpty()) {
        log.warn("버전 목록을 찾을 수 없어 크롤링 건너뜀 techStack={}", techStackName);
        return;
      }

      List<String> versions = versionsOpt.get();
      TechStack techStack = source.getTechStack();
      String lastProcessedVersion = null;

      for (String version : versions) {
        long releaseStartNs = System.nanoTime();
        if (releaseRecordRepository.existsByTechStackAndVersion(techStack, version)) {
          continue;
        }

        GithubClient.ReleaseDetails releaseDetails = null;
        String sourceUrl = null;
        String content = null;
        Instant publishedAt = null;
        String title = version;

        if (githubOwner != null && githubRepo != null) {
          Optional<GithubClient.ReleaseDetails> releaseDetailsOpt =
              githubClient.fetchReleaseDetails(githubOwner, githubRepo, version);
          if (releaseDetailsOpt.isEmpty()) {
            log.warn("릴리즈 노트를 찾을 수 없어 건너뜀 techStack={} version={}", techStackName, version);
            continue;
          }
          releaseDetails = releaseDetailsOpt.get();
          title = releaseDetails.title() != null ? releaseDetails.title() : version;
          content = releaseDetails.content();
          publishedAt = releaseDetails.publishedAt();
          sourceUrl = releaseDetails.htmlUrl();
        } else if (source.getType() == TechStackSourceType.NPM) {
          log.warn("GitHub 좌표 정보가 없어 릴리즈 노트 없이 저장 techStack={} version={}", techStackName, version);
        } else {
          log.warn("GitHub 좌표 정보 부족으로 크롤링 건너뜀 techStack={}", techStackName);
          return;
        }

        ReleaseRecord record =
            releaseRecordRepository.save(
                ReleaseRecord.builder()
                    .techStack(techStack)
                    .version(version)
                    .title(title)
                    .content(content)
                    .publishedAt(publishedAt)
                    .build());

        List<ReleaseEvent.Tag> eventTags = List.of();
        if (content != null) {
          Set<ReleaseTagType> tags = releaseParser.extractTags(content);
          eventTags =
              tags.stream()
                  .map(
                      tagType -> {
                        releaseTagRepository.save(
                            ReleaseTag.builder().releaseRecord(record).tagType(tagType).build());
                        return new ReleaseEvent.Tag(tagType.name(), "Auto-extracted");
                      })
                  .toList();
        }

        // Kafka 메시지 전송
        LocalDateTime publishedAtAtSeoul =
            record.getPublishedAt() != null
                ? LocalDateTime.ofInstant(record.getPublishedAt(), ZoneId.of("Asia/Seoul"))
                : null;
        kafkaProducer.sendReleaseEvent(
            new ReleaseEvent(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                new ReleaseEvent.Payload(
                    techStack.getName(),
                    version,
                    record.getTitle(),
                    record.getContent(),
                    null,
                    null,
                    List.of(),
                    null,
                    List.of(),
                    publishedAtAtSeoul,
                    sourceUrl,
                    eventTags)));

        if (content != null && sourceUrl != null) {
          enqueueTranslationBacklog(record, sourceUrl);
        }

        log.info(
            "릴리즈 크롤링 성공 techStack={} version={} title={}",
            techStackName,
            version,
            record.getTitle());
        long releaseMs = (System.nanoTime() - releaseStartNs) / 1_000_000L;
        log.trace(
            "릴리즈 처리 시간 techStack={} version={} elapsedMs={}", techStackName, version, releaseMs);

        lastProcessedVersion = version;
      }

      if (lastProcessedVersion != null) {
        techStack.updateLatestVersion(lastProcessedVersion);
        techStackRepository.save(techStack);
      }

      long totalMs = (System.nanoTime() - processStartNs) / 1_000_000L;
      log.info(
          "크롤링 완료 techStack={} processedUntil={} elapsedMs={}",
          techStackName,
          lastProcessedVersion,
          totalMs);
    } catch (Exception ex) {
      log.error("크롤링 실패 techStack={}", techStackName, ex);
    }
  }

  private void enqueueTranslationBacklog(ReleaseRecord record, String sourceUrl) {
    if (translationBacklogRepository.existsByReleaseRecordId(record.getId())) {
      return;
    }
    translationBacklogRepository.save(
        TranslationBacklog.builder()
            .releaseRecord(record)
            .status(TranslationBacklogStatus.PENDING)
            .retryCount(0)
            .lastError(null)
            .sourceUrl(sourceUrl)
            .build());
  }
}
