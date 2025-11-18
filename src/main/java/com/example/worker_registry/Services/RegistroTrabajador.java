package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.TrabajadorRepository;
import com.example.worker_registry.securtity.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroTrabajador {

    private final TrabajadorRepository repo;
    private final JwtService jwt;
    private final MailService mail;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.base-url}")
    private String baseUrl; // ej: http://localhost:8080

    @Value("${app.activation.auto:true}")
    private boolean activationAuto;

    public RegistroTrabajador(TrabajadorRepository repo, JwtService jwt, MailService mail) {
        this.repo = repo;
        this.jwt = jwt;
        this.mail = mail;
    }

    public java.util.List<Trabajador> obtenerTodosLosTrabajadores() {
        return repo.findAll();
    }

    @Transactional
    public Trabajador registrarTrabajador(Trabajador t) {
        if (t.getContrasena() == null || t.getConfirmarContrasena() == null) {
            throw new IllegalArgumentException("Debe ingresar y confirmar la contraseña");
        }
        if (!t.getContrasena().equals(t.getConfirmarContrasena())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }
        if (repo.existsByCorreo(t.getCorreo())) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }
        if (repo.existsByCelular(t.getCelular())) {
            throw new IllegalArgumentException("El número de celular ya está registrado");
        }

        t.setContrasena(encoder.encode(t.getContrasena()));
        t.setConfirmarContrasena(null);
        boolean autoActivate = activationAuto;
        t.setActivo(autoActivate);

        var saved = repo.save(t);

        if (autoActivate) {
            return saved;
        }

        // Token de activación y envío de correo
        try {
            var token = jwt.generateActivationToken(saved.getId());
            var link = baseUrl + "/api/v1/auth/verify?token=" + token;
            var subject = "Activa tu cuenta Conecta2";
            var body = """
                    Hola %s,

                    Gracias por registrarte en Conecta2. Para activar tu cuenta, haz clic aquí:

                    %s

                    Si no solicitaste este registro, ignora este mensaje.

                    Saludos,
                    Equipo Conecta2
                    """.formatted(saved.getNombreCompleto(), link);

            mail.send(saved.getCorreo(), subject, body);
        } catch (Exception e) {
            // No caigas en 500 si falla correo
            e.printStackTrace();
        }

        return saved;
    }

    public java.util.Optional<Trabajador> buscarPorId(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public void activarCuenta(Long id) {
        var t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));
        if (!t.isActivo()) {
            t.setActivo(true);
            repo.save(t);
        }
    }

    /**
     * Permite que un trabajador actualice los datos de su cuenta.
     */
    @Transactional
    public Trabajador actualizarTrabajador(Long id, Trabajador cambios) {
        var existente = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Trabajador no encontrado"));

        if (cambios.getNombreCompleto() != null && !cambios.getNombreCompleto().isBlank()) {
            existente.setNombreCompleto(cambios.getNombreCompleto());
        }

        if (cambios.getCorreo() != null && !cambios.getCorreo().isBlank()
                && !cambios.getCorreo().equalsIgnoreCase(existente.getCorreo())) {
            repo.findByCorreo(cambios.getCorreo())
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> { throw new RuntimeException("El correo ya esta registrado"); });
            existente.setCorreo(cambios.getCorreo());
        }

        if (cambios.getCelular() != null && !cambios.getCelular().isBlank()
                && !cambios.getCelular().equals(existente.getCelular())) {
            repo.findByCelular(cambios.getCelular())
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> { throw new RuntimeException("El numero de celular ya esta registrado"); });
            existente.setCelular(cambios.getCelular());
        }

        if (cambios.getAreaServicio() != null) {
            existente.setAreaServicio(cambios.getAreaServicio());
        }

        if (cambios.getContrasena() != null || cambios.getConfirmarContrasena() != null) {
            if (cambios.getContrasena() == null || cambios.getConfirmarContrasena() == null) {
                throw new IllegalArgumentException("Debe ingresar y confirmar la contrasena");
            }
            if (!cambios.getContrasena().equals(cambios.getConfirmarContrasena())) {
                throw new IllegalArgumentException("Las contrasenas no coinciden");
            }
            var contrasenaActual = cambios.getContrasenaActual();
            if (contrasenaActual == null || contrasenaActual.isBlank()) {
                throw new IllegalArgumentException("Debe ingresar la contrasena actual para modificarla");
            }
            if (!encoder.matches(contrasenaActual, existente.getContrasena())) {
                throw new IllegalArgumentException("La contrasena actual no es valida");
            }
            existente.setContrasena(encoder.encode(cambios.getContrasena()));
        }

        return repo.save(existente);
    }
}
