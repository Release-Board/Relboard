package io.relboard.crawler.infra.util;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

public final class HtmlToMarkdownUtil {

  private static final FlexmarkHtmlConverter CONVERTER =
      FlexmarkHtmlConverter.builder().build();

  private HtmlToMarkdownUtil() {}

  public static String convert(String html) {
    if (html == null || html.isBlank()) {
      return null;
    }
    String markdown = CONVERTER.convert(html);
    return markdown == null || markdown.isBlank() ? null : markdown;
  }
}
