package io.relboard.crawler.service.implementation;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class AiTranslationService {

  private final RestTemplate restTemplate;

  @Value("${ai.gemini.api-key:}")
  private String apiKey;

  @Value("${ai.gemini.model:gemini-2.5-flash-lite}")
  private String model;

  public AiTranslationService(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder.build();
  }

  public String translateToKorean(String content) {
    if (content == null || content.isBlank()) {
      return null;
    }
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("GEMINI_API_KEY is not set. Skip translation.");
      return null;
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
        return null;
      }
      JsonNode candidates = response.path("candidates");
      if (!candidates.isArray() || candidates.isEmpty()) {
        return null;
      }
      JsonNode textNode = candidates.get(0).path("content").path("parts");
      if (textNode.isArray() && !textNode.isEmpty()) {
        String text = textNode.get(0).path("text").asText(null);
        return text != null ? text.trim() : null;
      }
      return null;
    } catch (Exception ex) {
      log.error("[AI Translation Fail] {}", ex.getMessage(), ex);
      return null;
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
}
