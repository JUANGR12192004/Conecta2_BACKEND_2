package com.example.worker_registry.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleMapsConfig {

    private final String apiKey;

    public GoogleMapsConfig(@Value("${google.maps.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
