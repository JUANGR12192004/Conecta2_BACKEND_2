package com.example.worker_registry.Repository;

import com.example.worker_registry.Entitys.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {
    List<Oferta> findByServicio_Id(Long servicioId);
    List<Oferta> findByTrabajador_Id(Long trabajadorId);
    Optional<Oferta> findByServicio_IdAndTrabajador_Id(Long servicioId, Long trabajadorId);

    Optional<Oferta> findFirstByServicio_IdAndEstado(Long servicioId,
                                                     com.example.worker_registry.Entitys.EstadoNegociacion estado);

    java.util.List<Oferta> findByServicio_Cliente_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
            Long clienteId,
            com.example.worker_registry.Entitys.EstadoServicio estadoServicio,
            com.example.worker_registry.Entitys.EstadoNegociacion estadoNegociacion,
            com.example.worker_registry.Entitys.ParticipanteOferta ultimaPropuestaPor);

    java.util.List<Oferta> findByTrabajador_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
            Long trabajadorId,
            com.example.worker_registry.Entitys.EstadoServicio estadoServicio,
            com.example.worker_registry.Entitys.EstadoNegociacion estadoNegociacion,
            com.example.worker_registry.Entitys.ParticipanteOferta ultimaPropuestaPor);

    Optional<Oferta> findByPaymentIntentId(String paymentIntentId);

    void deleteByServicio_Id(Long servicioId);

    boolean existsByServicio_Id(Long servicioId);
}
