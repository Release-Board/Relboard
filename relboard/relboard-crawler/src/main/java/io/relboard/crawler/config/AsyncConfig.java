package io.relboard.crawler.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

  @Bean(name = "crawlerExecutor")
  public Executor crawlerExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("crawler-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(100);
    executor.setAllowCoreThreadTimeOut(true);
    executor.initialize();
    return executor;
  }

  @Bean
  public AsyncUncaughtExceptionHandler asyncExceptionHandler() {
    // Let Spring log uncaught async exceptions; service-level try-catch is still required per
    // guide.
    return new SimpleAsyncUncaughtExceptionHandler();
  }

  @Bean
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("crawler-scheduler-");
    scheduler.initialize();
    return scheduler;
  }
}
