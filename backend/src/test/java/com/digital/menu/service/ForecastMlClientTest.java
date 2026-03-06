package com.digital.menu.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ForecastMlClientTest {

    @Test
    void predict_returnsEmptyWhenDisabled() {
        ForecastMlClient client = new ForecastMlClient();
        ReflectionTestUtils.setField(client, "mlEnabled", false);
        ReflectionTestUtils.setField(client, "mlUrl", "http://localhost:9999/predict");

        var response = client.predict(Map.of("tenantId", "tenant-a"));
        assertTrue(response.isEmpty());
    }

    @Test
    void predict_returnsEmptyWhenUrlMissing() {
        ForecastMlClient client = new ForecastMlClient();
        ReflectionTestUtils.setField(client, "mlEnabled", true);
        ReflectionTestUtils.setField(client, "mlUrl", "");

        var response = client.predict(Map.of("tenantId", "tenant-a"));
        assertTrue(response.isEmpty());
    }
}

