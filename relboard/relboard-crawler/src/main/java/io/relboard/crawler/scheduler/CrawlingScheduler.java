package io.relboard.crawler.scheduler;

import io.relboard.crawler.domain.TechStackSource;
import io.relboard.crawler.repository.TechStackSourceRepository;
import io.relboard.crawler.service.abstraction.CrawlingService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final AtomicBoolean running = new AtomicBoolean(false);

  @Scheduled(cron = "${crawler.schedule.cron:0 */10 * * * *}")
  public void run() {
    if (!running.compareAndSet(false, true)) {
      log.info("크롤링 스케줄러가 이미 실행 중이라 건너뜀");
      return;
    }

    List<TechStackSource> sources = techStackSourceRepository.findAll();
    log.info("크롤링 스케줄러 시작 size={}", sources.size());

    try {
      for (TechStackSource source : sources) {
        try {
          crawlingService.process(source.getId());
        } catch (Exception ex) {
          log.error("스케줄러가 작업을 제출하지 못함 sourceId={}", source.getId(), ex);
        }
      }
    } finally {
      running.set(false);
    }
  }
}
