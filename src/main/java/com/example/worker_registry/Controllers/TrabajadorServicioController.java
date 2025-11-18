package com.example.worker_registry.Controllers;

import com.example.worker_registry.Services.ServicioTrabajadorService;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workers/services")
public class TrabajadorServicioController {

    private final ServicioTrabajadorService service;

    public TrabajadorServicioController(ServicioTrabajadorService service) {
        this.service = service;
    }

    @GetMapping("/available")
    public ResponseEntity<?> disponiblesPorArea(@AuthenticationPrincipal AuthenticatedUser user) {
        Long id = requireWorker(user);
        return ResponseEntity.ok(service.listarDisponiblesPorArea(id));
    }

    @GetMapping("/assigned")
    public ResponseEntity<?> serviciosAsignados(@AuthenticationPrincipal AuthenticatedUser user) {
        Long id = requireWorker(user);
        return ResponseEntity.ok(service.listarServiciosAsignados(id));
    }

    @PostMapping("/{servicioId}/offers")
    public ResponseEntity<?> ofertar(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable Long servicioId,
                                     @RequestBody ServicioTrabajadorService.CrearOferta body) {
        Long trabajadorId = requireWorker(user);
        var oferta = service.crearOferta(trabajadorId, servicioId, body);
        return ResponseEntity.status(201).body(Map.of(
                "id", oferta.getId(),
                "mensaje", "Oferta registrada correctamente"
        ));
    }

    private Long requireWorker(AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("WORKER")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
    }
}

