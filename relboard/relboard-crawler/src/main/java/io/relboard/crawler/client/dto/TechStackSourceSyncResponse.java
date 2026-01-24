package io.relboard.crawler.client.dto;

public record TechStackSourceSyncResponse(
    Long techStackId,
    String techStackName,
    String category,
    String colorHex,
    String type,
    String githubOwner,
    String githubRepo,
    String mavenGroupId,
    String mavenArtifactId) {}
