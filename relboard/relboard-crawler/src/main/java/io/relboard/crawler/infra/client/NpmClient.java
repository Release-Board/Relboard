package io.relboard.crawler.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class NpmClient {

  private final RestClient npmRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NpmClient(@Qualifier("npmRestClient") final RestClient npmRestClient) {
    this.npmRestClient = npmRestClient;
  }

  public Optional<List<String>> fetchVersions(String packageName) {
    if (packageName == null || packageName.isBlank()) {
      return Optional.empty();
    }
    try {
      JsonNode root =
          npmRestClient.get().uri("/" + packageName).retrieve().body(JsonNode.class);
      if (root == null) {
        return Optional.empty();
      }
      JsonNode timeNode = root.get("time");
      if (timeNode == null || !timeNode.isObject()) {
        return Optional.empty();
      }
      Map<String, String> timeMap =
          objectMapper.convertValue(timeNode, new com.fasterxml.jackson.core.type.TypeReference<>() {});
      List<Map.Entry<String, String>> entries = new ArrayList<>(timeMap.entrySet());
      entries.removeIf(entry -> "created".equals(entry.getKey()) || "modified".equals(entry.getKey()));
      entries.sort(Comparator.comparing(Map.Entry::getValue));
      List<String> versions = new ArrayList<>();
      for (Map.Entry<String, String> entry : entries) {
        versions.add(entry.getKey());
      }
      return versions.isEmpty() ? Optional.empty() : Optional.of(versions);
    } catch (Exception ex) {
      log.warn("NPM metadata 조회 실패 packageName={}", packageName, ex);
      return Optional.empty();
    }
  }
}
