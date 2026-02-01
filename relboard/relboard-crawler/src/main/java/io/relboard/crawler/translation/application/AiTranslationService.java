package io.relboard.crawler.translation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.relboard.crawler.translation.domain.BatchInsightResult;
import io.relboard.crawler.translation.domain.BatchTranslationResult;
import io.relboard.crawler.translation.domain.InsightPayload;
import io.relboard.crawler.translation.domain.TranslationBacklog;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    long startNs = System.nanoTime();
    if (backlogs == null || backlogs.isEmpty()) {
      log.trace("AI batch translate skipped: empty batch");
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

      long requestStartNs = System.nanoTime();
      String response =
          GoogleAiGeminiChatModel.builder().apiKey(apiKey).modelName(model).build().chat(prompt);
      long requestMs = (System.nanoTime() - requestStartNs) / 1_000_000L;
      log.trace(
          "AI batch translate request completed size={} elapsedMs={}", backlogs.size(), requestMs);

      String json = extractJsonArray(response);
      if (json == null) {
        return BatchTranslationResult.failed("invalid json response");
      }

      List<TranslationItem> items = parseItems(json);
      Map<Long, String> translations = new HashMap<>();
      Set<Long> expectedIds = new HashSet<>();
      for (TranslationBacklog backlog : backlogs) {
        expectedIds.add(backlog.getId());
      }
      for (TranslationItem item : items) {
        if (item == null || item.id() == null || item.translated() == null) {
          log.warn("AI response contains invalid item. Skip.");
          continue;
        }
        if (!expectedIds.contains(item.id())) {
          log.warn("AI response contains unexpected id. Skip id={}", item.id());
          continue;
        }
        translations.put(item.id(), item.translated().trim());
      }
      if (translations.isEmpty()) {
        return BatchTranslationResult.failed("no valid translations");
      }
      long totalMs = (System.nanoTime() - startNs) / 1_000_000L;
      log.trace("AI batch translate finished size={} elapsedMs={}", backlogs.size(), totalMs);
      return BatchTranslationResult.success(translations);
    } catch (Exception ex) {
      log.error("[AI Translation Fail] {}", ex.getMessage());
      return BatchTranslationResult.failed(ex.getMessage());
    }
  }

  public BatchInsightResult extractInsightsBatch(List<TranslationBacklog> backlogs) {
    long startNs = System.nanoTime();
    if (backlogs == null || backlogs.isEmpty()) {
      log.trace("AI insight batch skipped: empty batch");
      return BatchInsightResult.success(Map.of());
    }
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("GEMINI_API_KEY is not set. Skip insight extraction.");
      return BatchInsightResult.skippedNoKey();
    }
    GateResult gateResult = acquireQuotaSlot();
    if (gateResult != GateResult.OK) {
      return gateResult == GateResult.QUOTA_EXCEEDED
          ? BatchInsightResult.skippedQuota()
          : BatchInsightResult.failed("interrupted");
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
      String prompt = buildInsightPrompt(payloadJson);

      long requestStartNs = System.nanoTime();
      String response =
          GoogleAiGeminiChatModel.builder().apiKey(apiKey).modelName(model).build().chat(prompt);
      long requestMs = (System.nanoTime() - requestStartNs) / 1_000_000L;
      log.trace(
          "AI insight batch request completed size={} elapsedMs={}", backlogs.size(), requestMs);

      String json = extractJsonArray(response);
      if (json == null) {
        return BatchInsightResult.failed("invalid json response");
      }

      List<InsightItemResponse> items = parseInsightItems(json);
      Map<Long, InsightPayload> insights = new HashMap<>();
      Set<Long> expectedIds = new HashSet<>();
      for (TranslationBacklog backlog : backlogs) {
        expectedIds.add(backlog.getId());
      }
      for (InsightItemResponse item : items) {
        if (item == null || item.id() == null || item.shortSummary() == null) {
          log.warn("AI insight response contains invalid item. Skip.");
          continue;
        }
        if (!expectedIds.contains(item.id())) {
          log.warn("AI insight response contains unexpected id. Skip id={}", item.id());
          continue;
        }
        insights.put(
            item.id(),
            new InsightPayload(
                item.shortSummary(),
                item.insights(),
                item.migrationGuide(),
                item.technicalKeywords()));
      }
      if (insights.isEmpty()) {
        return BatchInsightResult.failed("no valid insights");
      }
      long totalMs = (System.nanoTime() - startNs) / 1_000_000L;
      log.trace("AI insight batch finished size={} elapsedMs={}", backlogs.size(), totalMs);
      return BatchInsightResult.success(insights);
    } catch (Exception ex) {
      log.error("[AI Insight Fail] {}", ex.getMessage());
      return BatchInsightResult.failed(ex.getMessage());
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

  private String buildInsightPrompt(String payloadJson) {
    return String.join(
        "\n",
        "너는 10년 차 시니어 풀스택 개발자이자 기술 블로그 에디터다.",
        "규칙:",
        "- 출력은 반드시 JSON 배열만 반환하고 다른 텍스트를 포함하지 말 것.",
        "- shortSummary는 개발자가 얻는 이득 중심으로 작성.",
        "- insights.type은 BREAKING/SECURITY/FEATURE/PERFORMANCE/FIX 중 하나.",
        "- reason은 본문 근거 기반으로 작성.",
        "- BREAKING이 있으면 migrationGuide를 반드시 채움.",
        "  - 가능하면 before/after 코드 제공.",
        "  - 불명확하면 checklist 1줄 가이드로 대체.",
        "- technicalKeywords는 5개 내외, 소문자, 중복 금지.",
        "",
        "응답 형식:",
        "[{\"id\": <id>, \"shortSummary\": \"...\", \"insights\": [{\"type\":\"BREAKING\",\"title\":\"...\",\"reason\":\"...\"}],",
        "\"migrationGuide\": {\"description\":\"...\",\"code\":{\"before\":\"...\",\"after\":\"...\"},\"checklist\":\"...\"},",
        "\"technicalKeywords\": [\"...\"]}]",
        "",
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

  private List<TranslationItem> parseItems(String json) throws JsonProcessingException {
    try {
      return objectMapper.readValue(json, new TypeReference<List<TranslationItem>>() {});
    } catch (JsonProcessingException ex) {
      log.error("[AI Translation Fail] json parse error: {}", ex.getMessage());
      String recovered = recoverJsonArray(json);
      if (recovered == null) {
        throw ex;
      }
      return parseItemsLenient(recovered);
    }
  }

  private List<InsightItemResponse> parseInsightItems(String json) throws JsonProcessingException {
    try {
      return objectMapper.readValue(json, new TypeReference<List<InsightItemResponse>>() {});
    } catch (JsonProcessingException ex) {
      log.error("[AI Insight Fail] json parse error: {}", ex.getMessage());
      String recovered = recoverJsonArray(json);
      if (recovered == null) {
        throw ex;
      }
      return parseInsightItemsLenient(recovered);
    }
  }

  private List<InsightItemResponse> parseInsightItemsLenient(String json)
      throws JsonProcessingException {
    List<InsightItemResponse> items = new ArrayList<>();
    try (var parser = objectMapper.getFactory().createParser(json)) {
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        return items;
      }
      while (parser.nextToken() == JsonToken.START_OBJECT) {
        try {
          InsightItemResponse item = objectMapper.readValue(parser, InsightItemResponse.class);
          items.add(item);
        } catch (Exception ex) {
          log.warn(
              "AI insight response partial parse stopped size={} reason={}",
              items.size(),
              ex.getMessage());
          break;
        }
      }
      if (!items.isEmpty()) {
        log.warn("AI insight response partial parse succeeded size={}", items.size());
      }
      return items;
    } catch (JsonProcessingException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new JsonProcessingException(ex.getMessage(), ex) {};
    }
  }

  private List<TranslationItem> parseItemsLenient(String json) throws JsonProcessingException {
    List<TranslationItem> items = new ArrayList<>();
    try (var parser = objectMapper.getFactory().createParser(json)) {
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        return items;
      }
      while (parser.nextToken() == JsonToken.START_OBJECT) {
        try {
          TranslationItem item = objectMapper.readValue(parser, TranslationItem.class);
          items.add(item);
        } catch (Exception ex) {
          log.warn(
              "AI response partial parse stopped size={} reason={}", items.size(), ex.getMessage());
          break;
        }
      }
      if (!items.isEmpty()) {
        log.warn("AI response partial parse succeeded size={}", items.size());
      }
      return items;
    } catch (JsonProcessingException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new JsonProcessingException(ex.getMessage(), ex) {};
    }
  }

  private String recoverJsonArray(String text) {
    if (text == null) {
      return null;
    }
    int start = text.indexOf('[');
    int lastBrace = text.lastIndexOf('}');
    if (start < 0 || lastBrace < start) {
      return null;
    }
    return text.substring(start, lastBrace + 1) + "]";
  }

  private enum GateResult {
    OK,
    QUOTA_EXCEEDED,
    INTERRUPTED
  }

  private record TranslationItem(Long id, String translated) {}

  private record InsightItemResponse(
      Long id,
      String shortSummary,
      List<InsightPayload.InsightItem> insights,
      InsightPayload.MigrationGuide migrationGuide,
      List<String> technicalKeywords) {}
}
