package io.relboard.crawler.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }

  @Bean
  @Qualifier("githubRestClient")
  public RestClient githubRestClient(
      RestClient.Builder builder, @Value("${github.token:}") String githubToken) {
    RestClient.Builder configured = builder.baseUrl("https://api.github.com");
    if (githubToken != null && !githubToken.isBlank()) {
      configured = configured.defaultHeader("Authorization", "Bearer " + githubToken);
    }
    return configured.defaultHeader("Accept", "application/vnd.github+json").build();
  }

  @Bean
  @Qualifier("mavenRestClient")
  public RestClient mavenRestClient(RestClient.Builder builder) {
    return builder.build();
  }
}
