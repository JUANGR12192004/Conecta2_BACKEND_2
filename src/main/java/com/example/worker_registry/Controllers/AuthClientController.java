package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Services.RegistroCliente;
import com.example.worker_registry.securtity.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/clients")
public class AuthClientController {

    private final RegistroCliente service;
    private final ClienteRepository repo;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthClientController(RegistroCliente service,
                                ClienteRepository repo,
                                JwtService jwt) {
        this.service = service;
        this.repo = repo;
        this.jwt = jwt;
    }

    // Accept POST to both /api/v1/auth/clients and /api/v1/auth/clients/register
    @PostMapping({"", "/register"})
    public ResponseEntity<?> register(@Valid @RequestBody Cliente cliente) {
        var saved = service.registrarCliente(cliente);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Registro recibido. Revisa tu correo para activar la cuenta."
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        var userId = jwt.parseActivationToken(token);
        service.activarCuenta(userId);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Cuenta de cliente activada correctamente. Ya puedes iniciar sesion."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = firstNonBlank(payload,
                "email", "correo", "correoElectronico", "correo_electronico", "username");
        String password = firstNonBlank(payload,
                "password", "contrasena", "clave");

        if (email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }

        var cliente = repo.findByCorreo(email).orElse(null);
        if (cliente == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }
        if (!cliente.isActivo()) {
            return ResponseEntity.status(403).body(Map.of("mensaje", "Cuenta no verificada. Revisa tu correo."));
        }
        if (!encoder.matches(password, cliente.getContrasena())) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }

        var access = jwt.generateAccessToken(cliente.getId(), "CLIENT");
        return ResponseEntity.ok(Map.of(
                "token", access,
                "userId", cliente.getId(),
                "nombreCompleto", cliente.getNombreCompleto(),
                "role", "CLIENT"
        ));
    }

    private String firstNonBlank(Map<String, String> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                var value = payload.get(key);
                if (value != null) {
                    var trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
        }
        return "";
    }
}
