package io.relboard.crawler.translation.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.relboard.crawler.translation.domain.TranslationResult;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class AiTranslationService {

  private final RestTemplate restTemplate;

  @Value("${ai.gemini.api-key:}")
  private String apiKey;

  @Value("${ai.gemini.model:gemini-2.5-flash-lite}")
  private String model;

  @Value("${ai.gemini.min-interval-ms:6000}")
  private long minIntervalMs;

  @Value("${ai.gemini.max-requests-per-day:20}")
  private int maxRequestsPerDay;

  private final Object rateLock = new Object();
  private long lastRequestAt = 0L;
  private int requestCountToday = 0;
  private LocalDate requestDate = LocalDate.now(ZoneOffset.UTC);

  public AiTranslationService(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder.build();
  }

  public TranslationResult translateWithStatus(String content) {
    if (content == null || content.isBlank()) {
      return TranslationResult.skippedEmpty();
    }
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("GEMINI_API_KEY is not set. Skip translation.");
      return TranslationResult.skippedNoKey();
    }
    GateResult gateResult = acquireQuotaSlot();
    if (gateResult != GateResult.OK) {
      return gateResult == GateResult.QUOTA_EXCEEDED
          ? TranslationResult.skippedQuota()
          : TranslationResult.failed("interrupted");
    }

    try {
      String url = String.format(
          "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
          model, apiKey);

      Map<String, Object> part = new HashMap<>();
      part.put("text", buildPrompt(content));

      Map<String, Object> contentObj = new HashMap<>();
      contentObj.put("parts", List.of(part));

      Map<String, Object> body = new HashMap<>();
      body.put("contents", List.of(contentObj));

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

      JsonNode response = restTemplate.postForObject(url, request, JsonNode.class);
      if (response == null) {
        return TranslationResult.failed("empty response");
      }
      JsonNode candidates = response.path("candidates");
      if (!candidates.isArray() || candidates.isEmpty()) {
        return TranslationResult.failed("empty candidates");
      }
      JsonNode textNode = candidates.get(0).path("content").path("parts");
      if (textNode.isArray() && !textNode.isEmpty()) {
        String text = textNode.get(0).path("text").asText(null);
        return text != null
            ? TranslationResult.success(text.trim())
            : TranslationResult.failed("empty text");
      }
      return TranslationResult.failed("empty content parts");
    } catch (HttpStatusCodeException ex) {
      String retryAfter = ex.getResponseHeaders() != null
          ? ex.getResponseHeaders().getFirst("Retry-After")
          : null;
      log.error("[AI Translation Fail] status={} retryAfter={} body={}",
          ex.getStatusCode().value(),
          retryAfter != null ? retryAfter : "-",
          truncate(ex.getResponseBodyAsString(), 800));
      if (log.isDebugEnabled()) {
        log.debug("[AI Translation Fail] exception", ex);
      }
      return TranslationResult.failed(ex.getMessage());
    } catch (Exception ex) {
      log.error("[AI Translation Fail] {}", ex.getMessage());
      if (log.isDebugEnabled()) {
        log.debug("[AI Translation Fail] exception", ex);
      }
      return TranslationResult.failed(ex.getMessage());
    }
  }

  public String translateToKorean(String content) {
    TranslationResult result = translateWithStatus(content);
    return result.status() == TranslationResult.Status.SUCCESS ? result.content() : null;
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return "-";
    }
    if (value.length() <= max) {
      return value;
    }
    return value.substring(0, max) + "...";
  }

  private GateResult acquireQuotaSlot() {
    synchronized (rateLock) {
      LocalDate today = LocalDate.now(ZoneOffset.UTC);
      if (!today.equals(requestDate)) {
        requestDate = today;
        requestCountToday = 0;
        lastRequestAt = 0L;
      }
      if (requestCountToday >= maxRequestsPerDay) {
        log.warn("Gemini daily request cap reached ({}). Skip translation.", maxRequestsPerDay);
        return GateResult.QUOTA_EXCEEDED;
      }
      long now = System.currentTimeMillis();
      long waitMs = Math.max(0L, minIntervalMs - (now - lastRequestAt));
      if (waitMs > 0) {
        try {
          Thread.sleep(waitMs);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return GateResult.INTERRUPTED;
        }
      }
      lastRequestAt = System.currentTimeMillis();
      requestCountToday++;
      return GateResult.OK;
    }
  }

  private String buildPrompt(String content) {
    return String.join("\n",
        "역할: Professional IT Technical Translator.",
        "규칙:",
        "- 요약 금지. 원문 정보를 빠짐없이 1:1 번역.",
        "- Markdown 구조(헤더, 리스트, 코드블록) 완전 유지.",
        "- 말투: 해요체.",
        "- 기술 용어는 필요 시 영문 병기 또는 원어 유지.",
        "",
        "다음 영문 릴리즈 노트를 한국어로 전체 번역해줘:",
        "---",
        content
    );
  }

  private enum GateResult {
    OK,
    QUOTA_EXCEEDED,
    INTERRUPTED
  }
}
