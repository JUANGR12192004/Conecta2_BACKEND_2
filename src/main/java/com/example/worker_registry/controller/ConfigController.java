package com.example.worker_registry.controller;

import com.example.worker_registry.config.GoogleMapsConfig;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes safe, minimal configuration for clients that need to initialize SDKs.
 * Currently returns the Google Maps API key to authenticated users.
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    private final GoogleMapsConfig googleMapsConfig;

    public ConfigController(GoogleMapsConfig googleMapsConfig) {
        this.googleMapsConfig = googleMapsConfig;
    }

    @GetMapping("/google-maps-key")
    public ResponseEntity<?> googleMapsKey(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!googleMapsConfig.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Google Maps API key is not configured"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "googleMapsApiKey", googleMapsConfig.getApiKey()
        ));
    }
}
