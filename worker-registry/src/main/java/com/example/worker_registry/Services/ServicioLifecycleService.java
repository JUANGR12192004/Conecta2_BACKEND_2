package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ServicioLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ServicioLifecycleService.class);

    private final ServicioRepository servicioRepository;
    private final OfertaRepository ofertaRepository;
    private final PushNotificationService pushNotificationService;

    public ServicioLifecycleService(ServicioRepository servicioRepository,
                                     OfertaRepository ofertaRepository,
                                     PushNotificationService pushNotificationService) {
        this.servicioRepository = servicioRepository;
        this.ofertaRepository = ofertaRepository;
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * Corre cada hora y elimina automáticamente los servicios pendientes que ya pasaron
     * de su fecha estimada sin haber generado ofertas o asignaciones.
     */
    @Transactional
    @Scheduled(cron = "0 0 * * * ?")
    public void revisarServiciosPendientes() {
        var serviciosPendientes = servicioRepository.findByEstado(EstadoServicio.PENDIENTE);
        if (serviciosPendientes.isEmpty()) {
            return;
        }

        LocalDate hoy = LocalDate.now();
        for (Servicio servicio : serviciosPendientes) {
            validarYProcesarServicio(servicio, hoy);
        }
    }

    private void validarYProcesarServicio(Servicio servicio, LocalDate hoy) {
        var fechaServicio = servicio.getFechaEstimada().toLocalDate();
        Long clienteId = servicio.getCliente() != null ? servicio.getCliente().getId() : null;

        if (fechaServicio.isBefore(hoy) && !tieneActividad(servicio)) {
            eliminarServicio(servicio, clienteId);
        }
    }

    private void eliminarServicio(Servicio servicio, Long clienteId) {
        String title = "Servicio eliminado";
        String body = String.format(
                "El servicio %s (id=%d) ha sido eliminado al pasar la fecha estimada.",
                servicio.getTitulo(), servicio.getId()
        );
        pushNotificationService.notifyCliente(clienteId, title, body);
        log.info("Servicio {} eliminado automáticamente por expiración", servicio.getId());
        servicioRepository.delete(servicio);
    }

    private boolean tieneActividad(Servicio servicio) {
        if (servicio.getAssignedWorkerId() != null) {
            return true;
        }
        return ofertaRepository.existsByServicio_Id(servicio.getId());
    }
}
