package com.digital.menu.service;

import com.digital.menu.dto.DemandForecastResponse;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ForecastMlClient {
    private static final Logger log = LoggerFactory.getLogger(ForecastMlClient.class);

    private final RestClient restClient = RestClient.create();

    @Value("${app.forecast.ml.enabled:false}")
    private boolean mlEnabled;

    @Value("${app.forecast.ml.url:}")
    private String mlUrl;

    public Optional<DemandForecastResponse> predict(Map<String, Object> payload) {
        if (!mlEnabled || mlUrl == null || mlUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            DemandForecastResponse response = restClient.post()
                .uri(mlUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(DemandForecastResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception ex) {
            log.warn("ML forecast request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}

