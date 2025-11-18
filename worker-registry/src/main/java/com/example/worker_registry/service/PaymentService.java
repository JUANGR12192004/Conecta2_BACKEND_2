package com.example.worker_registry.service;

import com.example.worker_registry.exceptions.StripeProcessingException;
import com.example.worker_registry.utils.StripeResponseMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PaymentService {

    private final String defaultCurrency;
    private final String publishableKey;
    private final List<String> defaultPaymentMethods;

    public PaymentService(
            @Value("${stripe.default-currency:COP}") String defaultCurrency,
            @Value("${stripe.publishable-key:}") String publishableKey,
            @Value("${stripe.payment-method-types:card}") String paymentMethods) {
        this.defaultCurrency = defaultCurrency;
        this.publishableKey = publishableKey;
        this.defaultPaymentMethods = parsePaymentMethods(paymentMethods);
    }

    public Map<String, Object> createPaymentIntent(Map<String, Object> payload) {
        try {
            long amount = parseAmount(payload.get("amount"));
            String description = Optional.ofNullable(payload.get("description"))
                    .map(Object::toString)
                    .orElse("Servicio en Conecta2");
            String currency = Optional.ofNullable(payload.get("currency"))
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .orElse(defaultCurrency);
            Map<String, String> metadata = extractMetadata(payload);
            List<String> paymentMethods = resolvePaymentMethodTypes(payload.get("payment_method_types"));

            Map<String, Object> request = new HashMap<>();
            request.put("amount", amount);
            request.put("currency", currency);
            request.put("description", description);
            List<String> resolved = paymentMethods.isEmpty() ? defaultPaymentMethods : paymentMethods;
            request.put("payment_method_types", resolved);
            if (!metadata.isEmpty()) {
                request.put("metadata", metadata);
            }

            PaymentIntent intent = PaymentIntent.create(request);
            return attachPublishableKey(StripeResponseMapper.mapPaymentIntent(intent));
        } catch (StripeException e) {
            throw new StripeProcessingException("create-intent", e);
        }
    }

    public Map<String, Object> confirmPayment(Map<String, Object> payload) {
        try {
            String intentId = Optional.ofNullable(payload.get("paymentIntentId"))
                    .map(Object::toString)
                    .orElseThrow(() -> new StripeProcessingException("paymentIntentId is required", 400));
            PaymentIntent intent = PaymentIntent.retrieve(intentId);
            Map<String, Object> confirmPayload = new HashMap<>();
            Optional.ofNullable(payload.get("paymentMethod"))
                    .map(Object::toString)
                    .ifPresent(method -> confirmPayload.put("payment_method", method));
            PaymentIntent confirmed = confirmPayload.isEmpty()
                    ? intent.confirm()
                    : intent.confirm(confirmPayload);
            return attachPublishableKey(StripeResponseMapper.mapPaymentIntent(confirmed));
        } catch (StripeException e) {
            throw new StripeProcessingException("confirm-payment", e);
        }
    }

    public Map<String, Object> retrievePaymentStatus(String intentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(intentId);
            return attachPublishableKey(StripeResponseMapper.mapPaymentIntent(intent));
        } catch (StripeException e) {
            throw new StripeProcessingException("retrieve-status", e);
        }
    }

    private long parseAmount(Object rawAmount) {
        if (rawAmount == null) {
            throw new StripeProcessingException("amount is required", 400);
        }
        try {
            return Long.parseLong(rawAmount.toString());
        } catch (NumberFormatException ex) {
            throw new StripeProcessingException("amount must be a number representing cents", 400);
        }
    }

    private Map<String, String> extractMetadata(Map<String, Object> payload) {
        Object rawMetadata = payload.get("metadata");
        if (rawMetadata instanceof Map<?, ?> explicit) {
            Map<String, String> explicitEntries = new LinkedHashMap<>();
            explicit.forEach((key, value) -> {
                if (key != null) {
                    explicitEntries.put(key.toString(), Objects.toString(value, ""));
                }
            });
            return explicitEntries;
        }
        return buildMetadata(payload);
    }

    private Map<String, Object> attachPublishableKey(Map<String, Object> payload) {
        if (publishableKey != null && !publishableKey.isBlank()) {
            payload.put("publishableKey", publishableKey);
        }
        return payload;
    }

    private Map<String, String> buildMetadata(Map<String, Object> payload) {
        Map<String, String> metadata = new LinkedHashMap<>();
        Set<String> skipKeys = Set.of("amount", "currency", "description", "payment_method_types", "metadata");
        payload.forEach((key, value) -> {
            if (key == null || skipKeys.contains(key)) {
                return;
            }
            metadata.put(key, Objects.toString(value, ""));
        });
        return metadata;
    }

    private List<String> resolvePaymentMethodTypes(Object raw) {
        List<String> methods = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Optional.ofNullable(entry)
                        .map(Object::toString)
                        .map(String::trim)
                        .map(normalize -> normalize.replaceAll("[^A-Za-z0-9]", ""))
                        .map(String::toLowerCase)
                        .ifPresent(methods::add);
            }
        }
        return methods;
    }

    private List<String> parsePaymentMethods(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("card");
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replaceAll("[^A-Za-z0-9]", "").toLowerCase())
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
