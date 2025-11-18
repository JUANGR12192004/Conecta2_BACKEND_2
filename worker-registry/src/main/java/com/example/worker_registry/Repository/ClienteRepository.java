package com.example.worker_registry.Repository;

import com.example.worker_registry.Entitys.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    // Ya existente
    Optional<Cliente> findByCorreo(String correo);
    boolean existsByCorreo(String correo);

    // 🔹 Nuevos métodos útiles para validaciones y flujos
    Optional<Cliente> findByCelular(String celular);
    boolean existsByCelular(String celular);
}
