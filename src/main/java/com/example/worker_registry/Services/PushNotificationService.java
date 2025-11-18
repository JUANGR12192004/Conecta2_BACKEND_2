package com.example.worker_registry.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    public void notifyCliente(Long clienteId, String title, String message) {
        log.info("[PUSH] Cliente {} | {}: {}", clienteId, title, message);
    }

    public void notifyTrabajador(Long trabajadorId, String title, String message) {
        log.info("[PUSH] Trabajador {} | {}: {}", trabajadorId, title, message);
    }

    public void notifyAssignment(Long clienteId, Long trabajadorId) {
        String title = "Pago confirmado";
        String message = "El pago fue confirmado. El trabajador ha sido asignado al servicio.";
        if (clienteId != null) {
            notifyCliente(clienteId, title, message);
        }
        if (trabajadorId != null) {
            notifyTrabajador(trabajadorId, title, message);
        }
    }
}
