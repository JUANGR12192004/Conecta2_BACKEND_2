package com.example.worker_registry.service;

import com.example.worker_registry.Services.MailService;
import com.example.worker_registry.Services.OfertaService;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Services.PushNotificationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final OfertaService ofertaService;
    private final PushNotificationService pushNotificationService;
    private final MailService mailService;

    public PaymentWebhookService(OfertaService ofertaService,
                                 PushNotificationService pushNotificationService,
                                 MailService mailService) {
        this.ofertaService = ofertaService;
        this.pushNotificationService = pushNotificationService;
        this.mailService = mailService;
    }

    @Transactional
    public void handleEvent(Map<String, Object> event) {
        Map<String, Object> payload = extractObject(event);
        if (payload.isEmpty()) {
            log.warn("Webhook payload does not contain intent data");
            return;
        }
        var result = ofertaService.actualizarEstadoPago(payload);
        dispatchNotifications(result);
    }

    @Transactional
    public OfertaService.PaymentUpdateResult handlePaymentPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return OfertaService.PaymentUpdateResult.noop("empty-payload");
        }
        var result = ofertaService.actualizarEstadoPago(payload);
        dispatchNotifications(result);
        return result;
    }

    private Map<String, Object> extractObject(Map<String, Object> event) {
        Object dataObj = event.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object object = data.get("object");
            if (object instanceof Map<?, ?> objectMap) {
                Map<String, Object> safe = new LinkedHashMap<>();
                objectMap.forEach((key, value) -> {
                    if (key != null) {
                        safe.put(key.toString(), value);
                    }
                });
                return safe;
            }
        }
        if (event.containsKey("id") && event.containsKey("status")) {
            Map<String, Object> clone = new LinkedHashMap<>();
            clone.put("id", event.get("id"));
            clone.put("status", event.get("status"));
            return clone;
        }
        return Map.of();
    }

    private void dispatchNotifications(OfertaService.PaymentUpdateResult result) {
        if (result == null || result.oferta() == null) {
            return;
        }
        Servicio servicio = result.servicio();
        Long workerId = result.oferta().getTrabajador() != null ? result.oferta().getTrabajador().getId() : null;
        if (result.markedPaid()) {
            if (servicio != null) {
                notifySuccess(servicio, workerId);
            }
        } else if (result.markedFailed()) {
            if (servicio != null) {
                notifyFailure(servicio);
            }
        }
    }

    private void notifySuccess(Servicio servicio, Long workerId) {
        Long clienteId = servicio.getCliente() != null ? servicio.getCliente().getId() : null;
        pushNotificationService.notifyAssignment(clienteId, workerId);
        if (servicio.getCliente() != null) {
            mailService.send(servicio.getCliente().getCorreo(),
                    "Pago confirmado en Conecta2",
                    "El pago para el servicio " + servicio.getTitulo() + " fue registrado con ?xito.");
        }
    }

    private void notifyFailure(Servicio servicio) {
        if (servicio.getCliente() != null) {
            pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                    "Pago rechazado",
                    "Hubo un problema al procesar el pago del servicio " + servicio.getTitulo() + ". Intenta de nuevo.");
            mailService.send(servicio.getCliente().getCorreo(),
                    "Pago rechazado en Conecta2",
                    "Hubo un problema al procesar el pago del servicio " + servicio.getTitulo() + ".");
        }
    }
}
