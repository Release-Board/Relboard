package io.relboard.crawler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.relboard.crawler.crawler.application.CrawlingServiceImpl;
import io.relboard.crawler.infra.client.GithubClient;
import io.relboard.crawler.infra.client.MavenClient;
import io.relboard.crawler.infra.kafka.KafkaProducer;
import io.relboard.crawler.release.domain.ReleaseRecord;
import io.relboard.crawler.release.repository.ReleaseRecordRepository;
import io.relboard.crawler.release.repository.ReleaseTagRepository;
import io.relboard.crawler.techstack.domain.TechStack;
import io.relboard.crawler.techstack.domain.TechStackSource;
import io.relboard.crawler.techstack.domain.TechStackSourceType;
import io.relboard.crawler.techstack.repository.TechStackRepository;
import io.relboard.crawler.techstack.repository.TechStackSourceRepository;
import io.relboard.crawler.translation.repository.TranslationBacklogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlingServiceImplTest {

  @Mock private TechStackSourceRepository techStackSourceRepository;
  @Mock private TechStackRepository techStackRepository;
  @Mock private ReleaseRecordRepository releaseRecordRepository;
  @Mock private ReleaseTagRepository releaseTagRepository;
  @Mock private MavenClient mavenClient;
  @Mock private GithubClient githubClient;
  @Mock private KafkaProducer kafkaProducer;
  @Mock private TranslationBacklogRepository translationBacklogRepository;

  private CrawlingServiceImpl crawlingService;

  @BeforeEach
  void setUp() {
    crawlingService =
        new CrawlingServiceImpl(
            techStackSourceRepository,
            techStackRepository,
            releaseRecordRepository,
            releaseTagRepository,
            mavenClient,
            githubClient,
            kafkaProducer,
            translationBacklogRepository);
  }

  @Test
  void process_skipsWhenNoNewVersion() {
    TechStack techStack = TechStack.builder().id(1L).name("spring").latestVersion("1.0.0").build();

    TechStackSource source =
        TechStackSource.builder()
            .id(10L)
            .techStack(techStack)
            .type(TechStackSourceType.MAVEN)
            .mavenGroupId("org.example")
            .mavenArtifactId("app")
            .githubOwner("owner")
            .githubRepo("repo")
            .build();

    when(techStackSourceRepository.findById(10L)).thenReturn(Optional.of(source));
    when(mavenClient.fetchVersions("org.example", "app")).thenReturn(Optional.of(List.of("1.0.0")));
    when(releaseRecordRepository.existsByTechStackAndVersion(techStack, "1.0.0")).thenReturn(true);

    crawlingService.process(10L);

    verify(releaseRecordRepository, never()).save(any());
    verify(releaseTagRepository, never()).save(any());
    verify(techStackRepository, never()).save(any());
  }

  @Test
  void process_savesReleaseAndTagsWhenNewVersion() {
    TechStack techStack = TechStack.builder().id(1L).name("spring").latestVersion("1.0.0").build();

    TechStackSource source =
        TechStackSource.builder()
            .id(20L)
            .techStack(techStack)
            .type(TechStackSourceType.MAVEN)
            .mavenGroupId("org.example")
            .mavenArtifactId("app")
            .githubOwner("owner")
            .githubRepo("repo")
            .build();

    when(techStackSourceRepository.findById(20L)).thenReturn(Optional.of(source));
    when(mavenClient.fetchVersions("org.example", "app"))
        .thenReturn(Optional.of(List.of("1.0.0", "1.1.0")));
    when(releaseRecordRepository.existsByTechStackAndVersion(techStack, "1.0.0")).thenReturn(true);
    when(releaseRecordRepository.existsByTechStackAndVersion(techStack, "1.1.0")).thenReturn(false);

    GithubClient.ReleaseDetails releaseDetails =
        new GithubClient.ReleaseDetails(
            "Release 1.1.0", "breaking fix docs", Instant.now(), "http://github.com/mock/url");
    when(githubClient.fetchReleaseDetails("owner", "repo", "1.1.0"))
        .thenReturn(Optional.of(releaseDetails));

    ReleaseRecord savedRecord =
        ReleaseRecord.builder()
            .id(100L)
            .techStack(techStack)
            .version("1.1.0")
            .title("Release 1.1.0")
            .content(releaseDetails.content())
            .publishedAt(releaseDetails.publishedAt())
            .build();
    when(releaseRecordRepository.save(any())).thenReturn(savedRecord);

    crawlingService.process(20L);

    verify(releaseRecordRepository).save(any());
    verify(releaseTagRepository, times(3)).save(any());
    verify(techStackRepository).save(techStack);
    assertThat(techStack.getLatestVersion()).isEqualTo("1.1.0");
  }
}
