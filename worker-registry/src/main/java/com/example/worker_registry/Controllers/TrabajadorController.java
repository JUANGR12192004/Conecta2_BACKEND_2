package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Services.RegistroTrabajador;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/Trabajadores")
public class TrabajadorController {

    private final RegistroTrabajador registroTrabajador;

    public TrabajadorController(RegistroTrabajador registroTrabajador) {
        this.registroTrabajador = registroTrabajador;
    }

    // Registrar un trabajador
    @PostMapping("/register")
    public Trabajador registrar(@RequestBody Trabajador trabajador) {
        return registroTrabajador.registrarTrabajador(trabajador);
    }

    // Obtener todos los trabajadores
    @GetMapping
    public List<Trabajador> obtenerTodos() {
        return registroTrabajador.obtenerTodosLosTrabajadores();
    }

    // Obtener trabajador por ID (solo acepta números para evitar conflicto con "/register")
    @GetMapping("/{id:\\d+}")
    public Optional<Trabajador> obtenerPorId(@PathVariable Long id) {
        return registroTrabajador.buscarPorId(id);
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Trabajador trabajador) {
        try {
            Trabajador actualizado = registroTrabajador.actualizarTrabajador(id, trabajador);
            return ResponseEntity.ok(actualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
