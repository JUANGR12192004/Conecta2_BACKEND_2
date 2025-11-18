package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Services.PushNotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OfertaService {

    private static final Logger log = LoggerFactory.getLogger(OfertaService.class);

    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfertaService(OfertaRepository ofertaRepository,
                         ServicioRepository servicioRepository,
                         PushNotificationService pushNotificationService) {
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
        this.pushNotificationService = pushNotificationService;
    }

    public List<Oferta> listarPendientesCliente(Long clienteId) {
        var ofertas = ofertaRepository.findByServicio_Cliente_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
                clienteId,
                EstadoServicio.PENDIENTE,
                EstadoNegociacion.EN_NEGOCIACION,
                ParticipanteOferta.TRABAJADOR
        );
        return filtrarOfertasConServicioVigente(ofertas);
    }

    public List<Oferta> listarPendientesTrabajador(Long trabajadorId) {
        var ofertas = ofertaRepository.findByTrabajador_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
                trabajadorId,
                EstadoServicio.PENDIENTE,
                EstadoNegociacion.EN_NEGOCIACION,
                ParticipanteOferta.CLIENTE
        );
        return filtrarOfertasConServicioVigente(ofertas);
    }

    @Transactional
    public ResultadoRespuesta responderOferta(Long clientId, Long ofertaId, ResponderOferta body) {
        String resolvedAction = resolveAction(body);
        return responderOferta(clientId, ofertaId, resolvedAction);
    }

    @Transactional
    public ResultadoRespuesta responderOferta(Long clientId, Long ofertaId, String action) {
        String normalizedAction = normalizeAction(action);
        if ("ACCEPT".equals(normalizedAction)) {
            return acceptOfferAsClient(clientId, ofertaId);
        }
        if (normalizedAction == null || "REJECT".equals(normalizedAction)) {
            Oferta oferta = ensureOfferWaitingForClient(clientId, ofertaId);
            oferta.setEstado(EstadoNegociacion.RECHAZADA);
            ofertaRepository.save(oferta);
            return new ResultadoRespuesta("Oferta rechazada", false, null);
        }
        throw new IllegalArgumentException("Acción no soportada: " + action);
    }

    @Transactional
    public ResultadoRespuesta acceptOfferAsClient(Long clientId, Long ofertaId) {
        Oferta oferta = ensureOfferWaitingForClient(clientId, ofertaId);
        return acceptOfferInternal(oferta, "Oferta aceptada", false);
    }

    @Transactional
    public ResultadoRespuesta acceptOfferAsWorker(Long workerId, Long ofertaId) {
        Oferta oferta = ensureOfferWaitingForWorker(workerId, ofertaId);
        return acceptOfferInternal(oferta, "Contraoferta aceptada", true);
    }

    private Oferta ensureOfferWaitingForClient(Long clientId, Long ofertaId) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        Servicio servicio = oferta.getServicio();
        validarClientePropietario(clientId, servicio);
        validarServicioPendiente(servicio);
        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("La oferta ya no se encuentra disponible para respuesta");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.TRABAJADOR) {
            throw new IllegalStateException("Solo puedes aceptar o rechazar ofertas activas del trabajador");
        }
        return oferta;
    }

    private Oferta ensureOfferWaitingForWorker(Long workerId, Long ofertaId) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        validarTrabajadorPropietario(workerId, oferta);
        Servicio servicio = oferta.getServicio();
        validarServicioPendiente(servicio);
        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociación ya fue cerrada");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.CLIENTE) {
            throw new IllegalStateException("No hay una contraoferta del cliente por responder");
        }
        return oferta;
    }

    @Transactional
    public Oferta contraOfertaCliente(Long clientId, Long ofertaId, ContraOferta data) {
        if (data == null || data.monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (data.monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        Servicio servicio = oferta.getServicio();

        validarClientePropietario(clientId, servicio);
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociacion ya fue cerrada");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.TRABAJADOR) {
            throw new IllegalStateException("Ya enviaste una contraoferta, espera la respuesta del trabajador");
        }

        oferta.setMonto(data.monto);
        oferta.setMontoCliente(data.monto);
        oferta.setUltimaPropuestaPor(ParticipanteOferta.CLIENTE);
        oferta.setMensaje(data.mensaje);

        return ofertaRepository.save(oferta);
    }

    @Transactional
    public Oferta contraOfertaTrabajador(Long trabajadorId, Long ofertaId, ContraOferta data) {
        if (data == null || data.monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (data.monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        validarTrabajadorPropietario(trabajadorId, oferta);

        Servicio servicio = oferta.getServicio();
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociacion ya fue cerrada");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.CLIENTE) {
            throw new IllegalStateException("Ya enviaste una contraoferta, espera la respuesta del cliente");
        }

        oferta.setMonto(data.monto);
        oferta.setMontoTrabajador(data.monto);
        oferta.setUltimaPropuestaPor(ParticipanteOferta.TRABAJADOR);
        oferta.setMensaje(data.mensaje);

        return ofertaRepository.save(oferta);
    }

    @Transactional
    public ResultadoRespuesta responderOfertaTrabajador(Long trabajadorId, Long ofertaId, ResponderOferta body) {
        String resolvedAction = resolveAction(body);
        return responderOfertaTrabajador(trabajadorId, ofertaId, resolvedAction);
    }

    @Transactional
    public ResultadoRespuesta responderOfertaTrabajador(Long trabajadorId, Long ofertaId, String action) {
        String normalizedAction = normalizeAction(action);
        if ("ACCEPT".equals(normalizedAction)) {
            return acceptOfferAsWorker(trabajadorId, ofertaId);
        }

        Oferta oferta = ensureOfferWaitingForWorker(trabajadorId, ofertaId);
        oferta.setEstado(EstadoNegociacion.RECHAZADA);
        ofertaRepository.save(oferta);
        return new ResultadoRespuesta("Contraoferta rechazada", false, null);
    }

    private void validarClientePropietario(Long clientId, Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null || !clientId.equals(servicio.getCliente().getId())) {
            throw new SecurityException("No tienes permiso para responder esta oferta");
        }
    }

    private void validarTrabajadorPropietario(Long workerId, Oferta oferta) {
        if (oferta.getTrabajador() == null || oferta.getTrabajador().getId() == null
                || !workerId.equals(oferta.getTrabajador().getId())) {
            throw new SecurityException("No puedes responder ofertas de otro trabajador");
        }
    }

    private void validarServicioPendiente(Servicio servicio) {
        if (servicio == null || (servicio.getEstado() != EstadoServicio.PENDIENTE && servicio.getEstado() != EstadoServicio.PENDIENTE_PAGO)) {
            throw new IllegalStateException("Solo puedes negociar sobre servicios PENDIENTES");
        }
        if (marcarServicioComoVencido(servicio)) {
            throw new IllegalStateException("El servicio ya expiro");
        }
    }

    private ResultadoRespuesta acceptOfferInternal(Oferta oferta, String mensaje, boolean porTrabajador) {
        Servicio servicio = oferta.getServicio();
        if (servicio != null) {
            servicio.setEstado(EstadoServicio.PENDIENTE_PAGO);
            if (oferta.getTrabajador() != null && oferta.getTrabajador().getId() != null) {
                servicio.setAssignedWorkerId(oferta.getTrabajador().getId());
            }
            servicioRepository.save(servicio);
        }
        oferta.setEstado(EstadoNegociacion.PENDIENTE_DE_PAGO);
        if (oferta.getMonto() != null) {
            oferta.setMontoAcordado(oferta.getMonto());
        }
        ofertaRepository.save(oferta);
        String respuesta = mensaje != null ? mensaje : "Oferta aceptada";
        if (porTrabajador) {
            notifyClientWorkerAccepted(servicio);
        } else {
            notifyWorkerClientAccepted(oferta);
        }
        return new ResultadoRespuesta(respuesta, true, servicio);
    }

    private void notificarPagoConfirmado(Servicio servicio, Long trabajadorId) {
        if (servicio == null) {
            return;
        }
        Long clienteId = servicio.getCliente() != null ? servicio.getCliente().getId() : null;
        pushNotificationService.notifyAssignment(clienteId, trabajadorId);
    }

    private String getString(Object value) {
        return value == null ? null : value.toString();
    }

    private List<Oferta> filtrarOfertasConServicioVigente(List<Oferta> ofertas) {
        LocalDate hoy = LocalDate.now();
        List<Oferta> vigentes = new ArrayList<>();
        List<Servicio> expirados = new ArrayList<>();
        for (Oferta oferta : ofertas) {
            Servicio servicio = oferta.getServicio();
            if (servicio == null) {
                continue;
            }
            if (servicio.getEstado() != EstadoServicio.PENDIENTE && servicio.getEstado() != EstadoServicio.PENDIENTE_PAGO) {
                continue;
            }
            if (servicio.getFechaEstimada() != null && servicio.getFechaEstimada().toLocalDate().isBefore(hoy)) {
                servicio.setEstado(EstadoServicio.CANCELADO);
                expirados.add(servicio);
                continue;
            }
            vigentes.add(oferta);
        }
        if (!expirados.isEmpty()) {
            servicioRepository.saveAll(expirados);
        }
        return vigentes;
    }

    private boolean marcarServicioComoVencido(Servicio servicio) {
        if (servicio.getFechaEstimada() == null) {
            return false;
        }
        if (servicio.getFechaEstimada().toLocalDate().isBefore(LocalDate.now())) {
            servicio.setEstado(EstadoServicio.CANCELADO);
            servicioRepository.save(servicio);
            return true;
        }
        return false;
    }

    private void notifyWorkerClientAccepted(Oferta oferta) {
        if (oferta == null || oferta.getTrabajador() == null || oferta.getTrabajador().getId() == null) {
            return;
        }
        pushNotificationService.notifyTrabajador(
                oferta.getTrabajador().getId(),
                "Pago pendiente",
                "El cliente aceptó la oferta, queda pendiente de pago."
        );
    }

    private void notifyClientWorkerAccepted(Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null) {
            return;
        }
        pushNotificationService.notifyCliente(
                servicio.getCliente().getId(),
                "Pago pendiente",
                "El trabajador aceptó la oferta, proceda al pago."
        );
    }

    @Transactional
    public PaymentUpdateResult actualizarEstadoPago(Map<String, Object> intent) {
        if (intent == null || intent.isEmpty()) {
            return PaymentUpdateResult.noop("empty-payload");
        }
        Map<String, Object> normalizedIntent = normalizeIntent(intent);
        String intentId = resolveIntentId(normalizedIntent);
        Long offerId = extractOfferId(normalizedIntent);
        if (intentId == null && offerId == null) {
            log.warn("No payment intent or offer id found in payload {}", normalizedIntent.keySet());
            return PaymentUpdateResult.noop("missing-identifiers");
        }
        Optional<Oferta> opt = findOferta(intentId, offerId);
        if (opt.isEmpty()) {
            log.warn("No oferta encontrada para intent={} offerId={}", intentId, offerId);
            return PaymentUpdateResult.noop("offer-not-found");
        }
        Oferta oferta = opt.get();
        String status = normalizeStatus(normalizedIntent);

        guardarIntentEnOferta(oferta, normalizedIntent);
        if (status != null) {
            oferta.setPaymentStatus(status.toUpperCase(Locale.ROOT));
        }

        Servicio servicio = oferta.getServicio();
        Long workerId = oferta.getTrabajador() != null ? oferta.getTrabajador().getId() : null;
        boolean markedPaid = false;
        boolean markedFailed = false;
        boolean updated = false;

        if (servicio != null && isSuccessStatus(status)) {
            EstadoServicio previous = servicio.getEstado();
            EstadoNegociacion previousNegotiation = oferta.getEstado();
            servicio.setEstado(EstadoServicio.ASIGNADO);
            if (workerId != null) {
                servicio.setAssignedWorkerId(workerId);
            }
            servicioRepository.save(servicio);
            oferta.setEstado(EstadoNegociacion.ASIGNADO);
            ofertaRepository.save(oferta);
            markedPaid = previous != EstadoServicio.ASIGNADO || previousNegotiation != EstadoNegociacion.ASIGNADO;
            updated = true;
            notificarPagoConfirmado(servicio, workerId);
            log.info("Pago confirmado en intent {}, servicio {} marcado como ASIGNADO", intentId, servicio.getId());
        } else if (servicio != null && isFailureStatus(status)) {
            servicio.setEstado(EstadoServicio.PENDIENTE);
            servicio.setAssignedWorkerId(null);
            servicioRepository.save(servicio);
            oferta.setEstado(EstadoNegociacion.EN_NEGOCIACION);
            oferta.setUltimaPropuestaPor(ParticipanteOferta.TRABAJADOR);
            ofertaRepository.save(oferta);
            markedFailed = true;
            updated = true;
            log.info("Pago rechazado en intent {}, servicio {} devuelto a PENDIENTE", intentId, servicio.getId());
        } else {
            ofertaRepository.save(oferta);
            updated = true;
        }

        return new PaymentUpdateResult(updated, markedPaid, markedFailed, status, oferta, servicio);
    }

    private Map<String, Object> normalizeIntent(Map<String, Object> intent) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (intent != null) {
            intent.forEach((key, value) -> {
                if (key != null) {
                    normalized.put(key.toString(), value);
                }
            });
        }
        String resolvedId = resolveIntentId(normalized);
        if (resolvedId != null) {
            normalized.put("id", resolvedId);
        }
        String status = normalizeStatus(normalized);
        if (status != null) {
            normalized.put("status", status);
        }
        return normalized;
    }

    private String resolveIntentId(Map<String, Object> intent) {
        String paymentIntentId = getString(intent.get("payment_intent"));
        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            return paymentIntentId;
        }
        return getString(intent.get("id"));
    }

    private String normalizeStatus(Map<String, Object> intent) {
        Object status = intent.get("status");
        if (status == null) {
            status = intent.get("payment_status");
        }
        if (status == null) {
            status = intent.get("paymentStatus");
        }
        if (status == null) {
            return null;
        }
        String normalized = status.toString().trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isSuccessStatus(String status) {
        if (status == null) return false;
        String normalized = status.toLowerCase(Locale.ROOT);
        return normalized.equals("succeeded") || normalized.equals("paid");
    }

    private boolean isFailureStatus(String status) {
        if (status == null) return false;
        String normalized = status.toLowerCase(Locale.ROOT);
        Set<String> failures = Set.of("requires_payment_method", "canceled", "failed");
        return failures.contains(normalized);
    }

    private void guardarIntentEnOferta(Oferta oferta, Map<String, Object> intent) {
        if (intent == null || intent.isEmpty()) return;
        Object id = intent.get("id");
        Object clientSecret = intent.get("client_secret");
        Object status = intent.get("status");
        Object metadata = intent.get("metadata");
        if (id != null) oferta.setPaymentIntentId(id.toString());
        if (clientSecret != null) oferta.setPaymentClientSecret(clientSecret.toString());
        if (status != null) {
            oferta.setPaymentStatus(status.toString().trim().toUpperCase(Locale.ROOT));
        }
        Map<String, Object> metadataPayload = new LinkedHashMap<>(basePaymentMetadata(oferta));
        metadataPayload.putAll(metadataFromObject(metadata));
        if (!metadataPayload.isEmpty()) {
            oferta.setPaymentMetadata(serializeMetadata(metadataPayload));
        }
    }

    private Optional<Oferta> findOferta(String intentId, Long offerId) {
        if (intentId != null) {
            Optional<Oferta> byIntent = ofertaRepository.findByPaymentIntentId(intentId);
            if (byIntent.isPresent()) {
                return byIntent;
            }
        }
        if (offerId != null) {
            return ofertaRepository.findById(offerId);
        }
        return Optional.empty();
    }

    private Long extractOfferId(Map<String, Object> intent) {
        Object metadata = intent.get("metadata");
        if (metadata instanceof Map<?, ?> meta) {
            Object raw = meta.get("offerId");
            Long parsed = parseLong(raw);
            if (parsed != null) return parsed;
            parsed = parseLong(meta.get("ofertaId"));
            if (parsed != null) return parsed;
            parsed = parseLong(meta.get("oferta_id"));
            if (parsed != null) return parsed;
        }
        Long direct = parseLong(intent.get("offerId"));
        if (direct != null) return direct;
        return parseLong(intent.get("ofertaId"));
    }

    private Long parseLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }


    public Oferta requireOfferPendingPayment(Long clientId, Long ofertaId) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        Servicio servicio = oferta.getServicio();
        validarClientePropietario(clientId, servicio);
        validarServicioPendiente(servicio);
        if (oferta.getEstado() != EstadoNegociacion.PENDIENTE_DE_PAGO) {
            throw new IllegalStateException("La oferta no está pendiente de pago");
        }
        return oferta;
    }

    public Map<String, Object> buildPaymentMetadata(Oferta oferta) {
        if (oferta == null) return Map.of();
        Map<String, Object> fallback = basePaymentMetadata(oferta);
        Map<String, Object> stored = readMetadata(oferta.getPaymentMetadata());
        Map<String, Object> combined = new LinkedHashMap<>(fallback);
        combined.putAll(stored);
        return combined;
    }

    public void updatePaymentIntent(Oferta oferta, Map<String, Object> intent) {
        guardarIntentEnOferta(oferta, intent);
        ofertaRepository.save(oferta);
    }

    private Map<String, Object> readMetadata(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            Map<?, ?> parsed = objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
            return normalizeMetadata(parsed);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private Map<String, Object> metadataFromObject(Object raw) {
        if (raw instanceof Map<?, ?> entries) {
            return normalizeMetadata(entries);
        }
        return Map.of();
    }

    private Map<String, Object> basePaymentMetadata(Oferta oferta) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        if (oferta == null) return fallback;
        if (oferta.getId() != null) {
            fallback.put("offerId", oferta.getId());
        }
        if (oferta.getServicio() != null && oferta.getServicio().getId() != null) {
            fallback.put("serviceId", oferta.getServicio().getId());
            if (oferta.getServicio().getCliente() != null && oferta.getServicio().getCliente().getId() != null) {
                fallback.put("clienteId", oferta.getServicio().getCliente().getId());
            }
        }
        if (oferta.getTrabajador() != null && oferta.getTrabajador().getId() != null) {
            fallback.put("trabajadorId", oferta.getTrabajador().getId());
        }
        return fallback;
    }

    private Map<String, Object> normalizeMetadata(Map<?, ?> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key == null || value == null) return;
            normalized.put(key.toString(), value);
        });
        return normalized;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la metadata de pago", e);
        }
    }

    public static class ResponderOferta {
        public String action; // EXPECTED: ACCEPT or REJECT
        public Boolean accept; // true = aceptar, false/null = rechazar
    }

    public static class ContraOferta {
        public BigDecimal monto;
        public String mensaje;
    }

    public record ResultadoRespuesta(String mensaje, boolean accepted, Servicio servicio) {}

    public record PaymentUpdateResult(boolean updated,
                                      boolean markedPaid,
                                      boolean markedFailed,
                                      String status,
                                      Oferta oferta,
                                      Servicio servicio) {
        public static PaymentUpdateResult noop(String status) {
            return new PaymentUpdateResult(false, false, false, status, null, null);
        }
    }

    private String normalizeAction(String action) {
        if (action == null) return null;
        var trimmed = action.trim().toUpperCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveAction(ResponderOferta body) {
        if (body == null) {
            return null;
        }
        if (body.action != null && !body.action.isBlank()) {
            return body.action;
        }
        if (body.accept != null) {
            return body.accept ? "ACCEPT" : "REJECT";
        }
        return null;
    }
}
