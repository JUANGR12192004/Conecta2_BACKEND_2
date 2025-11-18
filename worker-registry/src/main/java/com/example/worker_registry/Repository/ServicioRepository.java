package com.example.worker_registry.Repository;

import com.example.worker_registry.Entitys.CategoriaServicio;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    List<Servicio> findByEstado(EstadoServicio estado);

    List<Servicio> findByEstadoAndCategoria(EstadoServicio estado, CategoriaServicio categoria);

    List<Servicio> findByCliente_Id(Long clienteId);

    Optional<Servicio> findByIdAndCliente_Id(Long servicioId, Long clienteId);

    List<Servicio> findByAssignedWorkerIdAndEstado(Long assignedWorkerId, EstadoServicio estado);

}
