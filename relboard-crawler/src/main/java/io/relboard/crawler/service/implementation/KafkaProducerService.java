package io.relboard.crawler.service.implementation;

import io.relboard.crawler.config.KafkaProperties;
import io.relboard.crawler.event.ReleaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

  private final KafkaTemplate<String, ReleaseEvent> kafkaTemplate;
  private final KafkaProperties kafkaProperties;

  public void sendReleaseEvent(ReleaseEvent event) {
    log.info(
        "Kafka로 릴리즈 이벤트 전송: {} - {}", event.payload().techStackName(), event.payload().version());

    // techStackName를 키로 사용하여 파티션 순서 보장
    kafkaTemplate.send(kafkaProperties.getTopic(), event.payload().techStackName(), event);
  }
}
