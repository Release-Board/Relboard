package io.relboard.crawler.domain;

import java.time.Instant;

public record ReleaseNote(String version, String title, String content, Instant publishedAt) {}
