package io.relboard.crawler.infra.client;

import io.relboard.crawler.infra.client.dto.CommonApiResponse;
import io.relboard.crawler.infra.client.dto.TechStackSourceSyncResponse;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class RelboardServiceClient {

  private final RestTemplate restTemplate;

  @Value("${crawler.service.base-url:http://localhost:8081}")
  private String serviceBaseUrl;

  public List<TechStackSourceSyncResponse> fetchTechStackSources() {
    String url = serviceBaseUrl + "/api/v1/crawler/tech-stack-sources";
    ResponseEntity<CommonApiResponse<List<TechStackSourceSyncResponse>>> response =
        restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<
                CommonApiResponse<List<TechStackSourceSyncResponse>>>() {});
    CommonApiResponse<List<TechStackSourceSyncResponse>> body = response.getBody();
    if (body == null || !body.isSuccess() || body.getData() == null) {
      return Collections.emptyList();
    }
    return body.getData();
  }
}
