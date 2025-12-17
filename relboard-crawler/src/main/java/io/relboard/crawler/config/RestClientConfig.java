package io.relboard.crawler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${github.token:}")
    private String githubToken;

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient githubRestClient(RestClient.Builder builder) {
        RestClient.Builder spec = builder
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);

        if (!githubToken.isBlank()) {
            spec = spec.defaultHeader("Authorization", "Bearer " + githubToken);
        }

        return spec.build();
    }

    @Bean
    public RestClient mavenRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl("https://search.maven.org")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
