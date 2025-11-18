package com.example.worker_registry.Repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.worker_registry.Entitys.Trabajador;

public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {
  boolean existsByCorreo(String correo);
  boolean existsByCelular(String celular);
  Optional<Trabajador> findByCorreo(String correo);
  Optional<Trabajador> findByCelular(String celular);
}
