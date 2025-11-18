package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.*;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServicioClienteService {

    private final ServicioRepository repo;
    private final ClienteRepository clienteRepo;
    private final OfertaRepository ofertaRepo;

    public ServicioClienteService(ServicioRepository repo,
                                  ClienteRepository clienteRepo,
                                  OfertaRepository ofertaRepo) {
        this.repo = repo;
        this.clienteRepo = clienteRepo;
        this.ofertaRepo = ofertaRepo;
    }

    // ==========================
    // HU005: Publicar servicio
    // ==========================
    @Transactional
    public Servicio crearServicio(Long clienteId, Servicio s) {
        var cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no existe"));

        // Evitar updates accidentales via JSON
        s.setId(null);

        // Validacion de fecha (doble check por seguridad)
        if (s.getFechaEstimada() == null || s.getFechaEstimada().isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("La fecha estimada no puede ser anterior al dia actual");
        }
        if (s.getCategoria() == null) {
            throw new IllegalArgumentException("La categoria es obligatoria");
        }
        if (s.getTitulo() == null || s.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El titulo no puede estar vacio");
        }
        if (s.getDescripcion() == null || s.getDescripcion().isBlank()) {
            throw new IllegalArgumentException("La descripcion no puede estar vacia");
        }
        if (s.getUbicacion() == null || s.getUbicacion().isBlank()) {
            throw new IllegalArgumentException("La ubicacion es obligatoria");
        }

        s.setCliente(cliente);
        s.setEstado(EstadoServicio.PENDIENTE);

        return repo.save(s);
    }

    // Listado publico (disponibles)
    @Transactional
    public List<Servicio> listarDisponibles() {
        var pendientes = repo.findByEstado(EstadoServicio.PENDIENTE);
        return filtrarPendientesVigentes(pendientes);
    }

    // Mis servicios
    public List<Servicio> listarPorCliente(Long clienteId) {
        var servicios = repo.findByCliente_Id(clienteId);
        actualizarEstadosPorVencimiento(servicios);
        return servicios;
    }

    public Servicio obtenerPorId(Long id) {
        var servicio = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        actualizarEstadoPorVencimiento(servicio);
        return servicio;
    }

    /** Detalle para el propietario (valida permisos). */
    public Servicio obtenerDetallePropietario(Long clienteId, Long servicioId) {
        var servicio = repo.findByIdAndCliente_Id(servicioId, clienteId)
                .orElseThrow(() -> new SecurityException("No tienes permiso sobre este servicio"));
        actualizarEstadoPorVencimiento(servicio);
        return servicio;
    }

    // ==========================
    // HU006: Editar/Eliminar
    // ==========================
    @Transactional
    public Servicio editarServicio(Long clienteId, Long servicioId, UpdateData data) {
        var s = repo.findByIdAndCliente_Id(servicioId, clienteId)
                .orElseThrow(() -> new SecurityException("No tienes permiso sobre este servicio"));

        if (s.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo los servicios PENDIENTES pueden editarse");
        }
        if (estaVencido(s, LocalDate.now())) {
            s.setEstado(EstadoServicio.CANCELADO);
            repo.save(s);
            throw new IllegalStateException("El servicio ya expiro");
        }

        // Validaciones HU006
        if (isBlank(data.titulo)) throw new IllegalArgumentException("El titulo no puede estar vacio");
        if (isBlank(data.descripcion)) throw new IllegalArgumentException("La descripcion no puede estar vacia");
        if (isBlank(data.ubicacion)) throw new IllegalArgumentException("La ubicacion es obligatoria");
        if (data.fechaEstimada == null || data.fechaEstimada.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("La fecha estimada no puede ser anterior al dia actual");
        }

        s.setTitulo(data.titulo);
        s.setDescripcion(data.descripcion);
        s.setUbicacion(data.ubicacion);
        s.setFechaEstimada(data.fechaEstimada);

        if (data.categoria != null) s.setCategoria(data.categoria);

        return repo.save(s); // @PreUpdate setea actualizadoEn
    }

    @Transactional
    public ResultadoOperacion eliminarServicio(Long clienteId, Long servicioId) {
        var s = repo.findByIdAndCliente_Id(servicioId, clienteId)
                .orElseThrow(() -> new SecurityException("No tienes permiso sobre este servicio"));
        if (s.getEstado() != EstadoServicio.PENDIENTE) {
            if (s.getEstado() == EstadoServicio.EN_PROCESO || s.getEstado() == EstadoServicio.ASIGNADO) {
                return new ResultadoOperacion(false, "No se puede eliminar un servicio que ya fue aceptado");
            }
            return new ResultadoOperacion(false, "Solo los servicios PENDIENTES pueden eliminarse");
        }
        ofertaRepo.deleteByServicio_Id(servicioId);
        repo.delete(s);
        return new ResultadoOperacion(true, "Servicio eliminado correctamente");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ===== "DTO" interno para actualizacion (HU006) - no creamos clases externas
    public static class UpdateData {
        public String titulo;
        public String descripcion;
        public CategoriaServicio categoria; // opcional en edicion
        public String ubicacion;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        public LocalDateTime fechaEstimada;
    }

    public static record ResultadoOperacion(boolean exitoso, String mensaje) {}

    private List<Servicio> filtrarPendientesVigentes(List<Servicio> pendientes) {
        LocalDate hoy = LocalDate.now();
        List<Servicio> vigentes = new ArrayList<>();
        List<Servicio> expirados = new ArrayList<>();
        for (Servicio servicio : pendientes) {
            if (marcarComoVencido(servicio, hoy)) {
                expirados.add(servicio);
            } else {
                vigentes.add(servicio);
            }
        }
        if (!expirados.isEmpty()) {
            repo.saveAll(expirados);
        }
        return vigentes;
    }

    private void actualizarEstadosPorVencimiento(List<Servicio> servicios) {
        LocalDate hoy = LocalDate.now();
        List<Servicio> expirados = new ArrayList<>();
        for (Servicio servicio : servicios) {
            if (marcarComoVencido(servicio, hoy)) {
                expirados.add(servicio);
            }
        }
        if (!expirados.isEmpty()) {
            repo.saveAll(expirados);
        }
    }

    private void actualizarEstadoPorVencimiento(Servicio servicio) {
        if (servicio == null) {
            return;
        }
        if (marcarComoVencido(servicio, LocalDate.now())) {
            repo.save(servicio);
        }
    }

    private boolean marcarComoVencido(Servicio servicio, LocalDate hoy) {
        if (servicio.getEstado() == EstadoServicio.PENDIENTE && estaVencido(servicio, hoy)) {
            servicio.setEstado(EstadoServicio.CANCELADO);
            return true;
        }
        return false;
    }

    private boolean estaVencido(Servicio servicio, LocalDate hoy) {
        return servicio.getFechaEstimada() != null
                && servicio.getFechaEstimada().toLocalDate().isBefore(hoy);
    }
}
