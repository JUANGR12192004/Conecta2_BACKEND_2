package com.example.worker_registry.Controllers;

import com.example.worker_registry.Services.ServicioTrabajadorService;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workers")
public class TrabajadorServiciosAsignadosController {

    private final ServicioTrabajadorService trabajadorService;

    public TrabajadorServiciosAsignadosController(ServicioTrabajadorService trabajadorService) {
        this.trabajadorService = trabajadorService;
    }

    @GetMapping("/{workerId}/services/assigned")
    public ResponseEntity<?> serviciosAsignados(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long workerId) {
        Long resolvedId = requireWorker(user, workerId);
        return ResponseEntity.ok(trabajadorService.listarServiciosAsignados(resolvedId));
    }

    private Long requireWorker(AuthenticatedUser user, Long workerId) {
        if (user == null) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("WORKER")) {
            throw new AccessDeniedException("Rol no autorizado");
        }
        if (!user.userId().equals(workerId)) {
            throw new AccessDeniedException("Solo puedes acceder a tus servicios");
        }
        return workerId;
    }
}
