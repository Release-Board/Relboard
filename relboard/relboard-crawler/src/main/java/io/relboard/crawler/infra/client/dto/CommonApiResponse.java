package io.relboard.crawler.infra.client.dto;

import lombok.Getter;

@Getter
public class CommonApiResponse<T> {
  private boolean success;
  private T data;
  private ErrorResponse error;

  @Getter
  public static class ErrorResponse {
    private String code;
    private String message;
    private Object errors;
  }
}
