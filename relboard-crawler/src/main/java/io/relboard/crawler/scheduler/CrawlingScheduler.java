package io.relboard.crawler.scheduler;

import io.relboard.crawler.repository.TechStackSourceRepository;
import io.relboard.crawler.service.abstraction.CrawlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlingScheduler {

    private final TechStackSourceRepository techStackSourceRepository;
    private final CrawlingService crawlingService;

    @Scheduled(cron = "${crawler.schedule.cron:0 */10 * * * *}")
    public void run() {
        var sources = techStackSourceRepository.findAll();
        log.info("정기 크롤링 시작 targets={}", sources.size());

        for (var source : sources) {
            try {
                crawlingService.process(source.getId());
            } catch (Exception ex) {
                log.error("스케줄러가 작업을 제출하지 못함 sourceId={}", source.getId(), ex);
            }
        }
    }
}
