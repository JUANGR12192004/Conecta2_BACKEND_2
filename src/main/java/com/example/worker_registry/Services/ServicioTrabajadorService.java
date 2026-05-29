package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServicioTrabajadorService {

    private final ServicioRepository servicioRepo;
    private final TrabajadorRepository trabajadorRepo;
    private final OfertaRepository ofertaRepo;
    private final OfertaService ofertaService;

    public ServicioTrabajadorService(ServicioRepository servicioRepo,
                                     TrabajadorRepository trabajadorRepo,
                                     OfertaRepository ofertaRepo,
                                     OfertaService ofertaService) {
        this.servicioRepo = servicioRepo;
        this.trabajadorRepo = trabajadorRepo;
        this.ofertaRepo = ofertaRepo;
        this.ofertaService = ofertaService;
    }

    @Transactional
    public List<Servicio> listarDisponiblesPorArea(Long trabajadorId) {
        var trabajador = trabajadorRepo.findById(trabajadorId)
                .orElseThrow(() -> new EntityNotFoundException("Trabajador no encontrado"));
        var categoria = trabajador.getAreaServicio();
        if (categoria == null) {
            throw new IllegalStateException("El trabajador no tiene un area de servicio valida");
        }
        var serviciosPendientes = new ArrayList<>(
                servicioRepo.findByEstadoAndCategoria(EstadoServicio.PENDIENTE, categoria)
        );
        var serviciosPendientePagoAsignados = servicioRepo.findByAssignedWorkerIdAndEstado(trabajadorId, EstadoServicio.PENDIENTE_PAGO);
        serviciosPendientes.addAll(serviciosPendientePagoAsignados);
        return filtrarServiciosVigentes(serviciosPendientes);
    }

    public List<Servicio> listarServiciosAsignados(Long trabajadorId) {
        return servicioRepo.findByAssignedWorkerIdAndEstado(trabajadorId, EstadoServicio.ASIGNADO);
    }

    @Transactional
    public Oferta crearOferta(Long trabajadorId, Long servicioId, CrearOferta data) {
        if (data == null || data.monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (data.monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        var trabajador = trabajadorRepo.findById(trabajadorId)
                .orElseThrow(() -> new EntityNotFoundException("Trabajador no encontrado"));
        var servicio = servicioRepo.findById(servicioId)
                .orElseThrow(() -> new EntityNotFoundException("Servicio no encontrado"));

        if (servicio.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo se puede ofertar en servicios PENDIENTES");
        }
        if (estaVencido(servicio, LocalDate.now())) {
            servicio.setEstado(EstadoServicio.CANCELADO);
            servicioRepo.save(servicio);
            throw new IllegalStateException("El servicio ya expiro");
        }

        var categoriaTrabajador = trabajador.getAreaServicio();
        if (categoriaTrabajador == null) {
            throw new IllegalStateException("El trabajador no tiene un area de servicio valida");
        }
        if (servicio.getCategoria() != categoriaTrabajador) {
            throw new IllegalArgumentException("El servicio no corresponde a tu area de servicio");
        }

        var ofertaExistente = ofertaRepo.findByServicio_IdAndTrabajador_Id(servicioId, trabajadorId);
        var negociacionActiva = ofertaRepo.findFirstByServicio_IdAndEstado(servicioId, EstadoNegociacion.EN_NEGOCIACION);
        if (negociacionActiva.isPresent()) {
            var activa = negociacionActiva.get();
            Long trabajadorEnNegociacion = activa.getTrabajador() != null ? activa.getTrabajador().getId() : null;
            boolean esOtroTrabajador = trabajadorEnNegociacion != null && !trabajadorEnNegociacion.equals(trabajadorId);
            if (esOtroTrabajador) {
                throw new IllegalStateException("El servicio ya cuenta con una negociacion en curso");
            }
        }

        if (ofertaExistente.isPresent() && ofertaExistente.get().getEstado() == EstadoNegociacion.EN_NEGOCIACION) {
            var contra = new OfertaService.ContraOferta();
            contra.monto = data.monto;
            contra.mensaje = data.mensaje;
            return ofertaService.contraOfertaTrabajador(trabajadorId, ofertaExistente.get().getId(), contra);
        }

        var oferta = ofertaExistente.orElseGet(() -> Oferta.builder()
                .servicio(servicio)
                .trabajador(trabajador)
                .build());

        if (oferta.getEstado() == EstadoNegociacion.PENDIENTE_DE_PAGO) {
            throw new IllegalStateException("Esta oferta ya se encuentra aceptada");
        }

        oferta.setMonto(data.monto);
        oferta.setMensaje(data.mensaje);
        oferta.setEstado(EstadoNegociacion.EN_NEGOCIACION);
        oferta.setUltimaPropuestaPor(ParticipanteOferta.TRABAJADOR);
        oferta.setMontoTrabajador(data.monto);
        oferta.setMontoCliente(null);
        oferta.setMontoAcordado(null);

        return ofertaRepo.save(oferta);
    }

    private List<Servicio> filtrarServiciosVigentes(List<Servicio> servicios) {
        LocalDate hoy = LocalDate.now();
        List<Servicio> vigentes = new ArrayList<>();
        List<Servicio> expirados = new ArrayList<>();
        for (Servicio servicio : servicios) {
            if (servicio.getEstado() == EstadoServicio.PENDIENTE && estaVencido(servicio, hoy)) {
                servicio.setEstado(EstadoServicio.CANCELADO);
                expirados.add(servicio);
            } else {
                vigentes.add(servicio);
            }
        }
        if (!expirados.isEmpty()) {
            servicioRepo.saveAll(expirados);
        }
        return vigentes;
    }

    private boolean estaVencido(Servicio servicio, LocalDate hoy) {
        return servicio.getFechaEstimada() != null
                && servicio.getFechaEstimada().toLocalDate().isBefore(hoy);
    }

    public static class CrearOferta {
        public BigDecimal monto;
        public String mensaje;
    }
}
