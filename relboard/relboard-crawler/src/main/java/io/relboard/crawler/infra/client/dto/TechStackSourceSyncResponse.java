package io.relboard.crawler.infra.client.dto;

import java.util.List;

public record TechStackSourceSyncResponse(
    Long techStackId,
    String techStackName,
    String category,
    String colorHex,
    String type,
    List<TechStackSourceMetadataResponse> metadata) {

  public record TechStackSourceMetadataResponse(String key, String value) {}
}
