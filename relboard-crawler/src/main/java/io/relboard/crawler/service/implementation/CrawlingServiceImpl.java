package io.relboard.crawler.service.implementation;

import io.relboard.crawler.client.GithubClient;
import io.relboard.crawler.client.MavenClient;
import io.relboard.crawler.domain.ReleaseNote;
import io.relboard.crawler.domain.ReleaseParser;
import io.relboard.crawler.domain.ReleaseRecord;
import io.relboard.crawler.domain.ReleaseTag;
import io.relboard.crawler.domain.ReleaseTagType;
import io.relboard.crawler.domain.TechStack;
import io.relboard.crawler.domain.TechStackSource;
import io.relboard.crawler.repository.ReleaseRecordRepository;
import io.relboard.crawler.repository.ReleaseTagRepository;
import io.relboard.crawler.repository.TechStackRepository;
import io.relboard.crawler.repository.TechStackSourceRepository;
import io.relboard.crawler.service.abstraction.CrawlingService;
import java.util.Optional;
import java.util.Set;
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
    private final ReleaseParser releaseParser = new ReleaseParser();

    @Async("crawlerExecutor")
    @Transactional
    @Override
    public void process(Long sourceId) {
        try {
            log.info("크롤링 시작 sourceId={}", sourceId);

            TechStackSource source = techStackSourceRepository.findById(sourceId)
                    .orElseThrow(() -> new IllegalArgumentException("TechStackSource not found: " + sourceId));

            if (!source.hasMavenCoordinates() || !source.hasGithubCoordinates()) {
                log.warn("좌표 정보 부족으로 크롤링 건너뜀 sourceId={}", sourceId);
                return;
            }

            Optional<String> latestVersionOpt = mavenClient.fetchLatestVersion(source.getMavenGroupId(), source.getMavenArtifactId());
            if (latestVersionOpt.isEmpty()) {
                log.warn("최신 버전을 찾을 수 없어 크롤링 건너뜀 sourceId={}", sourceId);
                return;
            }

            String latestVersion = latestVersionOpt.get();
            TechStack techStack = source.getTechStack();
            if (latestVersion.equals(techStack.getLatestVersion())) {
                log.info("신규 버전 없음 sourceId={} latestVersion={}", sourceId, latestVersion);
                return;
            }

            Optional<ReleaseNote> releaseNoteOpt = githubClient.fetchReleaseNote(source.getGithubOwner(), source.getGithubRepo(), latestVersion);
            if (releaseNoteOpt.isEmpty()) {
                log.warn("릴리즈 노트를 찾을 수 없어 크롤링 건너뜀 sourceId={} version={} ", sourceId, latestVersion);
                return;
            }

            ReleaseNote releaseNote = releaseNoteOpt.get();
            ReleaseRecord record = releaseRecordRepository.save(ReleaseRecord.builder()
                    .techStack(techStack)
                    .version(latestVersion)
                    .title(releaseNote.title())
                    .content(releaseNote.content())
                    .publishedAt(releaseNote.publishedAt())
                    .build());

            Set<ReleaseTagType> tags = releaseParser.extractTags(releaseNote.content());
            tags.forEach(tag -> releaseTagRepository.save(ReleaseTag.builder()
                    .releaseRecord(record)
                    .tagType(tag)
                    .build()));

            techStack.updateLatestVersion(latestVersion);
            techStackRepository.save(techStack);

            log.info("크롤링 완료 sourceId={} version={}", sourceId, latestVersion);
        } catch (Exception ex) {
            log.error("크롤링 실패 sourceId={}", sourceId, ex);
        }
    }
}
