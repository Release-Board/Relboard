package io.relboard.crawler.domain;

/** Maven에서 조회한 버전 정보를 표현. GitHub 릴리스 상세는 별도 DTO로 관리한다. */
public record ReleaseNote(String version) {}
