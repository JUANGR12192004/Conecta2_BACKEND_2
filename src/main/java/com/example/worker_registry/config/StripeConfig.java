package com.example.worker_registry.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private final String apiKey;

    public StripeConfig(@Value("${stripe.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Stripe API key is required");
        }
        Stripe.apiKey = apiKey;
    }
}
