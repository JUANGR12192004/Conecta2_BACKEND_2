package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Repository.TrabajadorRepository;
import com.example.worker_registry.Services.RegistroTrabajador;
import com.example.worker_registry.securtity.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthTrabajadorController {

    private final RegistroTrabajador regService;
    private final TrabajadorRepository repo;
    private final ClienteRepository clienteRepo;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthTrabajadorController(RegistroTrabajador regService,
                                    TrabajadorRepository repo,
                                    ClienteRepository clienteRepo,
                                    JwtService jwt) {
        this.regService = regService;
        this.repo = repo;
        this.clienteRepo = clienteRepo;
        this.jwt = jwt;
    }

    @PostMapping({"/workers", "/workers/register"})
    public ResponseEntity<?> registerWorker(@Valid @RequestBody Trabajador trabajador) {
        var saved = regService.registrarTrabajador(trabajador);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Registro recibido. Revisa tu correo para activar la cuenta."
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        try {
            Long userId = jwt.parseActivationToken(token);
            regService.activarCuenta(userId);
            return ResponseEntity.ok(Map.of("mensaje", "Cuenta activada. Ya puedes iniciar sesion."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = firstNonBlank(payload,
                "email", "correo", "correoElectronico", "correo_electronico", "username");
        String password = firstNonBlank(payload,
                "password", "contrasena", "clave");
        String role = firstNonBlank(payload,
                "role", "rol", "userType", "user_type", "accountType", "account_type", "tipoUsuario");

        if (email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }

        if ("CLIENT".equalsIgnoreCase(role) || "CLIENTE".equalsIgnoreCase(role)) {
            return authenticateClient(email, password);
        }
        if ("WORKER".equalsIgnoreCase(role) || "TRABAJADOR".equalsIgnoreCase(role)) {
            return authenticateWorker(email, password);
        }

        var workerResponse = authenticateWorkerIfPossible(email, password);
        if (workerResponse != null) {
            return workerResponse;
        }

        var clientResponse = authenticateClientIfPossible(email, password);
        if (clientResponse != null) {
            return clientResponse;
        }

        return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
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

    private ResponseEntity<?> authenticateWorker(String email, String password) {
        var trabajador = repo.findByCorreo(email).orElse(null);
        if (trabajador == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }
        return buildWorkerLoginResponse(trabajador, password);
    }

    private ResponseEntity<?> authenticateClient(String email, String password) {
        var cliente = clienteRepo.findByCorreo(email).orElse(null);
        if (cliente == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }
        return buildClientLoginResponse(cliente, password);
    }

    private ResponseEntity<?> authenticateWorkerIfPossible(String email, String password) {
        return repo.findByCorreo(email)
                .map(trabajador -> buildWorkerLoginResponse(trabajador, password))
                .orElse(null);
    }

    private ResponseEntity<?> authenticateClientIfPossible(String email, String password) {
        return clienteRepo.findByCorreo(email)
                .map(cliente -> buildClientLoginResponse(cliente, password))
                .orElse(null);
    }

    private ResponseEntity<?> buildWorkerLoginResponse(Trabajador trabajador, String password) {
        if (!trabajador.isActivo()) {
            return ResponseEntity.status(403).body(Map.of("mensaje", "Cuenta no verificada. Revisa tu correo."));
        }
        if (!encoder.matches(password, trabajador.getContrasena())) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }

        var access = jwt.generateAccessToken(trabajador.getId(), "WORKER");
        return ResponseEntity.ok(Map.of(
                "token", access,
                "userId", trabajador.getId(),
                "nombre", trabajador.getNombreCompleto(),
                "role", "WORKER"
        ));
    }

    private ResponseEntity<?> buildClientLoginResponse(Cliente cliente, String password) {
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
}
