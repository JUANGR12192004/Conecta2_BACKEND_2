package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Repository.TrabajadorRepository;
import com.example.worker_registry.Services.RegistroTrabajador;
import com.example.worker_registry.securtity.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

class AuthTrabajadorControllerTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private RegistroTrabajador registroTrabajador;
    private TrabajadorRepository trabajadorRepository;
    private ClienteRepository clienteRepository;
    private JwtService jwtService;
    private AuthTrabajadorController controller;

    @BeforeEach
    void setUp() {
        registroTrabajador = Mockito.mock(RegistroTrabajador.class);
        trabajadorRepository = Mockito.mock(TrabajadorRepository.class);
        clienteRepository = Mockito.mock(ClienteRepository.class);
        jwtService = Mockito.mock(JwtService.class);
        controller = new AuthTrabajadorController(
                registroTrabajador,
                trabajadorRepository,
                clienteRepository,
                jwtService
        );
    }

    @Test
    void login_autenticaClienteCuandoSeIndicaRolClient() {
        var cliente = Cliente.builder()
                .id(9L)
                .correo("cliente@test.com")
                .contrasena(ENCODER.encode("secret123"))
                .nombreCompleto("Cliente Demo")
                .activo(true)
                .build();

        when(clienteRepository.findByCorreo("cliente@test.com")).thenReturn(Optional.of(cliente));
        when(jwtService.generateAccessToken(9L, "CLIENT")).thenReturn("token-client");

        var response = controller.login(Map.of(
                "email", "cliente@test.com",
                "password", "secret123",
                "role", "CLIENT"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("token-client", body.get("token"));
        assertEquals("CLIENT", body.get("role"));
    }

    @Test
    void login_autenticaClienteSinRolCuandoNoExisteTrabajador() {
        var cliente = Cliente.builder()
                .id(12L)
                .correo("fallback@test.com")
                .contrasena(ENCODER.encode("secret123"))
                .nombreCompleto("Fallback Cliente")
                .activo(true)
                .build();

        when(trabajadorRepository.findByCorreo("fallback@test.com")).thenReturn(Optional.empty());
        when(clienteRepository.findByCorreo("fallback@test.com")).thenReturn(Optional.of(cliente));
        when(jwtService.generateAccessToken(12L, "CLIENT")).thenReturn("token-fallback");

        var response = controller.login(Map.of(
                "email", "fallback@test.com",
                "password", "secret123"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("token-fallback", body.get("token"));
        assertEquals("CLIENT", body.get("role"));
    }
}
