package com.example.worker_registry.controller;

import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Services.OfertaService;
import com.example.worker_registry.exceptions.StripeProcessingException;
import com.example.worker_registry.securtity.AuthenticatedUser;
import com.example.worker_registry.service.PaymentService;
import com.example.worker_registry.service.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final OfertaService ofertaService;
    private final PaymentWebhookService paymentWebhookService;

    public PaymentController(PaymentService paymentService,
                             OfertaService ofertaService,
                             PaymentWebhookService paymentWebhookService) {
        this.paymentService = paymentService;
        this.ofertaService = ofertaService;
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/intents")
    public ResponseEntity<?> createIntent(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestBody Map<String, Object> payload) {
        try {
            Long clientId = requireClientRole(user);
            Long offerId = extractOfferId(payload);
            if (offerId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "offerId is required"));
            }
            Oferta oferta = ofertaService.requireOfferPendingPayment(clientId, offerId);
            Map<String, Object> request = buildIntentPayload(oferta, payload);
            Map<String, Object> intent = paymentService.createPaymentIntent(request);
            ofertaService.updatePaymentIntent(oferta, intent);
            return ResponseEntity.ok(buildIntentResponse(intent, oferta));
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> intent = paymentService.confirmPayment(payload);
            return ResponseEntity.ok(intent);
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        try {
            Map<String, Object> intent = paymentService.retrievePaymentStatus(id);
            return ResponseEntity.ok(intent);
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        paymentWebhookService.handleEvent(payload);
        return ResponseEntity.ok(Map.of("received", true));
    }

    private Long requireClientRole(AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("CLIENT")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
    }

    private Map<String, Object> buildIntentPayload(Oferta oferta, Map<String, Object> overrides) {
        BigDecimal baseAmount = oferta.getMontoAcordado() != null ? oferta.getMontoAcordado() : oferta.getMonto();
        if (baseAmount == null) {
            throw new IllegalStateException("La oferta no tiene monto definido");
        }
        BigDecimal amount = baseAmount;
        if (overrides != null && overrides.get("amount") != null) {
            amount = new BigDecimal(overrides.get("amount").toString());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInCents(amount));
        Servicio servicio = oferta.getServicio();
        String description = overrides != null && overrides.get("description") != null
                ? overrides.get("description").toString()
                : (servicio != null && servicio.getTitulo() != null ? servicio.getTitulo() : "Servicio en Conecta2");
        payload.put("description", description);
        if (overrides != null && overrides.get("currency") != null) {
            payload.put("currency", overrides.get("currency"));
        }
        List<String> methods = extractPaymentMethods(overrides);
        payload.put("payment_method_types", methods);
        Map<String, Object> metadata = new LinkedHashMap<>(ofertaService.buildPaymentMetadata(oferta));
        if (overrides != null && overrides.get("metadata") instanceof Map<?, ?> overrideMetadata) {
            overrideMetadata.forEach((key, value) -> {
                if (key != null) {
                    metadata.put(key.toString(), value);
                }
            });
        }
        metadata.put("offerId", oferta.getId());
        metadata.put("ofertaId", oferta.getId());
        metadata.put("oferta_id", oferta.getId());
        payload.put("metadata", metadata);
        return payload;
    }

    private Map<String, Object> buildIntentResponse(Map<String, Object> intent, Oferta oferta) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("payment_intent_id", intent.get("id"));
        response.put("client_secret", intent.get("clientSecret"));
        response.put("payment_status", intent.get("status"));
        response.put("payment_metadata", oferta.getPaymentMetadata());
        response.put("metadata", intent.get("metadata"));
        response.put("offerId", oferta.getId());
        return response;
    }

    private List<String> extractPaymentMethods(Map<String, Object> overrides) {
        if (overrides != null && overrides.get("payment_method_types") instanceof List<?> custom) {
            List<String> methods = custom.stream()
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
            if (!methods.isEmpty()) {
                return methods;
            }
        }
        return List.of("card");
    }

    private Long extractOfferId(Map<String, Object> payload) {
        if (payload == null) return null;
        Object raw = payload.get("offerId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private long amountInCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
}
