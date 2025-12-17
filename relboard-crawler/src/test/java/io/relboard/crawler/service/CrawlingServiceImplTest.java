package io.relboard.crawler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.relboard.crawler.client.GithubClient;
import io.relboard.crawler.client.MavenClient;
import io.relboard.crawler.domain.ReleaseRecord;
import io.relboard.crawler.domain.TechStack;
import io.relboard.crawler.domain.TechStackSource;
import io.relboard.crawler.domain.TechStackSourceType;
import io.relboard.crawler.repository.ReleaseRecordRepository;
import io.relboard.crawler.repository.ReleaseTagRepository;
import io.relboard.crawler.repository.TechStackRepository;
import io.relboard.crawler.repository.TechStackSourceRepository;
import io.relboard.crawler.service.implementation.CrawlingServiceImpl;
import java.time.Instant;
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
            githubClient);
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
    when(mavenClient.fetchLatestVersion("org.example", "app")).thenReturn(Optional.of("1.0.0"));

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
    when(mavenClient.fetchLatestVersion("org.example", "app")).thenReturn(Optional.of("1.1.0"));

    GithubClient.ReleaseDetails releaseDetails =
        new GithubClient.ReleaseDetails("Release 1.1.0", "breaking fix docs", Instant.now());
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
