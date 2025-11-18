package com.example.worker_registry.Controllers;

import com.example.worker_registry.Services.OfertaService;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class OfertaController {

    private final OfertaService ofertaService;

    public OfertaController(OfertaService ofertaService) {
        this.ofertaService = ofertaService;
    }

    @GetMapping("/clients/{clientId}/offers/pending")
    public ResponseEntity<?> pendientesCliente(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable Long clientId) {
        Long resolvedId = requireClient(user, clientId);
        return ResponseEntity.ok(ofertaService.listarPendientesCliente(resolvedId));
    }

    @GetMapping("/workers/{workerId}/offers/pending")
    public ResponseEntity<?> pendientesTrabajador(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long workerId) {
        Long resolvedId = requireWorker(user, workerId);
        return ResponseEntity.ok(ofertaService.listarPendientesTrabajador(resolvedId));
    }

    @PostMapping("/offers/{id}/respond")
    public ResponseEntity<?> responder(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) OfertaService.ResponderOferta body,
                                       @RequestParam(name = "accept", required = false) Boolean acceptParam) {
        Long clientId = requireClientRole(user);
        var result = ofertaService.responderOferta(clientId, id, resolveAction(body, acceptParam));
        return ResponseEntity.ok(buildResponse(result));
    }

    @PostMapping("/offers/{id}/counter")
    public ResponseEntity<?> contraOferta(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable Long id,
                                          @RequestBody OfertaService.ContraOferta body) {
        Long clientId = requireClientRole(user);
        var oferta = ofertaService.contraOfertaCliente(clientId, id, body);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Contraoferta registrada",
                "ofertaId", oferta.getId(),
                "monto", oferta.getMonto()
        ));
    }

    @PostMapping("/offers/{id}/worker/counter")
    public ResponseEntity<?> contraOfertaTrabajador(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long id,
                                                    @RequestBody OfertaService.ContraOferta body) {
        Long workerId = requireWorkerRole(user);
        var oferta = ofertaService.contraOfertaTrabajador(workerId, id, body);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Contraoferta registrada",
                "ofertaId", oferta.getId(),
                "monto", oferta.getMonto()
        ));
    }

    @PostMapping("/offers/{id}/worker/respond")
    public ResponseEntity<?> responderTrabajador(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long id,
                                                 @RequestBody(required = false) OfertaService.ResponderOferta body,
                                                 @RequestParam(name = "accept", required = false) Boolean acceptParam) {
        Long workerId = requireWorkerRole(user);
        var action = resolveAction(body, acceptParam);
        var result = ofertaService.responderOfertaTrabajador(workerId, id, action);
        return ResponseEntity.ok(buildResponse(result));
    }

    private Long requireClient(AuthenticatedUser user, Long expectedId) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("CLIENT")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
    }

    private Long requireWorker(AuthenticatedUser user, Long expectedId) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("WORKER")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
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

    private Long requireWorkerRole(AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("WORKER")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
    }

    private Map<String, Object> buildResponse(OfertaService.ResultadoRespuesta result) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("mensaje", result.mensaje());
        payload.put("accepted", result.accepted());
        if (result.servicio() != null) {
            payload.put("servicio", result.servicio());
        }
        return payload;
    }

    private String resolveAction(OfertaService.ResponderOferta body, Boolean acceptParam) {
        if (body != null) {
            if (body.action != null && !body.action.isBlank()) {
                return body.action;
            }
            if (body.accept != null) {
                return body.accept ? "ACCEPT" : "REJECT";
            }
        }
        if (acceptParam != null) {
            return acceptParam ? "ACCEPT" : "REJECT";
        }
        return null;
    }
}
