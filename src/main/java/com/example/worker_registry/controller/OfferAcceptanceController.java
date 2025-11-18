package com.example.worker_registry.controller;

import com.example.worker_registry.Services.OfertaService;
import com.example.worker_registry.Services.OfertaService.ResultadoRespuesta;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/offers")
public class OfferAcceptanceController {

    private final OfertaService ofertaService;

    public OfferAcceptanceController(OfertaService ofertaService) {
        this.ofertaService = ofertaService;
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptOffer(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable Long id) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        ResultadoRespuesta result;
        if (user.hasRole("CLIENT")) {
            result = ofertaService.acceptOfferAsClient(user.userId(), id);
        } else if (user.hasRole("WORKER")) {
            result = ofertaService.acceptOfferAsWorker(user.userId(), id);
        } else {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        if (!result.accepted()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", 409,
                    "message", "No fue posible aceptar la oferta",
                    "details", result
            ));
        }
        return ResponseEntity.ok(buildResponse(result));
    }

    private Map<String, Object> buildResponse(ResultadoRespuesta result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("mensaje", result.mensaje());
        payload.put("accepted", result.accepted());
        if (result.servicio() != null) {
            payload.put("servicio", result.servicio());
        }
        return payload;
    }
}
