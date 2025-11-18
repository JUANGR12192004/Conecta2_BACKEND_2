package com.example.worker_registry.utils;

import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StripeResponseMapper {

    private StripeResponseMapper() {
        // Utility holder
    }

    public static Map<String, Object> mapPaymentIntent(PaymentIntent intent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", intent.getId());
        map.put("status", intent.getStatus());
        map.put("amount", intent.getAmount());
        map.put("currency", intent.getCurrency());
        map.put("description", intent.getDescription());
        map.put("clientSecret", intent.getClientSecret());
        map.put("client_secret", intent.getClientSecret());
        map.put("paymentMethod", intent.getPaymentMethod());
        map.put("paymentMethodTypes", intent.getPaymentMethodTypes());
        map.put("customer", intent.getCustomer());
        map.put("metadata", intent.getMetadata());
        map.put("created", intent.getCreated());
        map.put("livemode", intent.getLivemode());
        Map<String, Object> lastPaymentError = mapLastPaymentError(intent.getLastPaymentError());
        map.put("lastPaymentError", lastPaymentError);
        return map;
    }

    private static Map<String, Object> mapLastPaymentError(StripeError error) {
        if (error == null) {
            return null;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", error.getMessage());
        details.put("code", error.getCode());
        details.put("declineCode", error.getDeclineCode());
        details.put("type", error.getType());
        return details;
    }
}
