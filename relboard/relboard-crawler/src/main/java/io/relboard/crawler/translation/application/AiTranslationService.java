package io.relboard.crawler.translation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.relboard.crawler.translation.domain.BatchTranslationResult;
import io.relboard.crawler.translation.domain.TranslationBacklog;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTranslationService {

  private final ObjectMapper objectMapper;

  @Value("${ai.gemini.api-key:}")
  private String apiKey;

  @Value("${ai.gemini.model:gemini-1.5-flash}")
  private String model;

  @Value("${ai.gemini.min-interval-ms:6000}")
  private long minIntervalMs;

  @Value("${ai.gemini.max-requests-per-day:20}")
  private int maxRequestsPerDay;

  private final Object rateLock = new Object();
  private long lastRequestAt = 0L;
  private int requestCountToday = 0;
  private LocalDate requestDate = LocalDate.now(ZoneOffset.UTC);

  public BatchTranslationResult translateBatch(List<TranslationBacklog> backlogs) {
    if (backlogs == null || backlogs.isEmpty()) {
      return BatchTranslationResult.success(Map.of());
    }
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("GEMINI_API_KEY is not set. Skip translation.");
      return BatchTranslationResult.skippedNoKey();
    }
    GateResult gateResult = acquireQuotaSlot();
    if (gateResult != GateResult.OK) {
      return gateResult == GateResult.QUOTA_EXCEEDED
          ? BatchTranslationResult.skippedQuota()
          : BatchTranslationResult.failed("interrupted");
    }

    try {
      List<Map<String, Object>> payload =
          backlogs.stream()
              .map(
                  backlog ->
                      Map.<String, Object>of(
                          "id", backlog.getId(),
                          "content", backlog.getReleaseRecord().getContent()))
              .toList();
      String payloadJson = objectMapper.writeValueAsString(payload);
      String prompt = buildBatchPrompt(payloadJson);

      String response =
          GoogleAiGeminiChatModel.builder().apiKey(apiKey).modelName(model).build().chat(prompt);

      String json = extractJsonArray(response);
      if (json == null) {
        return BatchTranslationResult.failed("invalid json response");
      }

      List<TranslationItem> items =
          objectMapper.readValue(json, new TypeReference<List<TranslationItem>>() {});
      Map<Long, String> translations = new HashMap<>();
      Set<Long> expectedIds = new HashSet<>();
      for (TranslationBacklog backlog : backlogs) {
        expectedIds.add(backlog.getId());
      }
      for (TranslationItem item : items) {
        if (item.id() == null || item.translated() == null) {
          return BatchTranslationResult.failed("missing id or translated");
        }
        if (!expectedIds.contains(item.id())) {
          return BatchTranslationResult.failed("unexpected id: " + item.id());
        }
        translations.put(item.id(), item.translated().trim());
      }
      if (translations.size() != expectedIds.size()) {
        return BatchTranslationResult.failed("response id count mismatch");
      }
      return BatchTranslationResult.success(translations);
    } catch (JsonProcessingException ex) {
      log.error("[AI Translation Fail] json parse error: {}", ex.getMessage());
      return BatchTranslationResult.failed("json parse error");
    } catch (Exception ex) {
      log.error("[AI Translation Fail] {}", ex.getMessage());
      return BatchTranslationResult.failed(ex.getMessage());
    }
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

  private String buildBatchPrompt(String payloadJson) {
    return String.join(
        "\n",
        "역할: Professional IT Technical Translator.",
        "규칙:",
        "- 요약 금지. 원문 정보를 빠짐없이 1:1 번역.",
        "- Markdown 구조(헤더, 리스트, 코드블록) 완전 유지.",
        "- 말투: 해요체.",
        "- 기술 용어는 필요 시 영문 병기 또는 원어 유지.",
        "- 반드시 JSON 배열로만 응답하고 다른 텍스트를 포함하지 말 것.",
        "",
        "다음 JSON 배열의 content를 한국어로 번역해줘.",
        "응답 형식: [{\"id\": <id>, \"translated\": \"<korean>\"}, ...]",
        "JSON 배열:",
        payloadJson);
  }

  private String extractJsonArray(String text) {
    if (text == null) {
      return null;
    }
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start < 0 || end < 0 || end <= start) {
      return null;
    }
    return text.substring(start, end + 1);
  }

  private enum GateResult {
    OK,
    QUOTA_EXCEEDED,
    INTERRUPTED
  }

  private record TranslationItem(Long id, String translated) {}
}
