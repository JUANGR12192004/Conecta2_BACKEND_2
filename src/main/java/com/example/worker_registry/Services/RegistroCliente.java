package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.securtity.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RegistroCliente {

    private final ClienteRepository clienteRepository;
    private final JwtService jwtService;
    private final MailService mailService;

    // Encoder local
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Para construir el enlace de activacion
    @Value("${app.base-url}")
    private String baseUrl;

    // Controla si se auto-activa o se envia correo de verificacion
    @Value("${app.activation.auto:true}")
    private boolean activationAuto;

    public RegistroCliente(ClienteRepository clienteRepository,
                           JwtService jwtService,
                           MailService mailService) {
        this.clienteRepository = clienteRepository;
        this.jwtService = jwtService;
        this.mailService = mailService;
    }

    /**
     * HU: mantiene la logica original y anade validaciones y notificaciones.
     */
    @Transactional
    public Cliente registrarCliente(Cliente cliente) {
        String password = cliente.getContrasena();
        String confirm = cliente.getConfirmarContrasena();

        if (isBlank(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena es obligatoria");
        }
        if (confirm != null && confirm.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe confirmar la contrasena");
        }
        if (confirm == null) {
            confirm = password;
        }
        if (!password.equals(confirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las contrasenas no coinciden");
        }

        if (clienteRepository.existsByCorreo(cliente.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya esta registrado");
        }

        if (!isBlank(cliente.getCelular()) && clienteRepository.existsByCelular(cliente.getCelular())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El numero de celular ya esta registrado");
        }

        cliente.setContrasena(encoder.encode(password));
        cliente.setConfirmarContrasena(null);
        // Activación condicional (evita bloqueo de SMTP en Render)
        boolean autoActivate = activationAuto;

        cliente.setActivo(autoActivate);

        Cliente saved = clienteRepository.save(cliente);

        // Solo envia correo si no hay auto-activación
        if (!autoActivate) {
            String token = jwtService.generateActivationToken(saved.getId());
            String link = baseUrl + "/api/v1/auth/clients/verify?token=" + token;

            String subject = "Activa tu cuenta Conecta2 (Cliente)";
            String body = """
                    Hola %s,

                    Gracias por registrarte en Conecta2. Para activar tu cuenta, haz clic en el siguiente enlace:

                    %s

                    Si no solicitaste este registro, ignora este mensaje.

                    Saludos,
                    Equipo Conecta2
                    """.formatted(saved.getNombreCompleto(), link);

            mailService.send(saved.getCorreo(), subject, body);
        }

        return saved;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Mantiene la logica original.
     */
    public Cliente obtenerClientePorCorreo(String correo) {
        return clienteRepository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    public Cliente obtenerClientePorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    /**
     * Mantiene la logica original.
     */
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    /**
     * Activa la cuenta desde el controlador de verificacion.
     */
    @Transactional
    public void activarCuenta(Long id) {
        Cliente c = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no existe"));
        if (!c.isActivo()) {
            c.setActivo(true);
            clienteRepository.save(c);
        }
    }

    /**
     * Reenvia el correo de activacion si la cuenta aun no esta activa.
     */
    public void reenviarActivacion(String email) {
        Cliente c = clienteRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (c.isActivo()) {
            throw new IllegalStateException("La cuenta ya esta activa");
        }

        String token = jwtService.generateActivationToken(c.getId());
        String link = baseUrl + "/api/v1/auth/clients/verify?token=" + token;

        String subject = "Reenvio: activa tu cuenta Conecta2 (Cliente)";
        String body = """
                Hola %s,

                Aqui tienes un nuevo enlace para activar tu cuenta:

                %s

                Saludos,
                Equipo Conecta2
                """.formatted(c.getNombreCompleto(), link);

        mailService.send(c.getCorreo(), subject, body);
    }

    /**
     * Permite que un cliente actualice los datos de su cuenta.
     */
    @Transactional
    public Cliente actualizarCliente(Long id, Cliente cambios) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (cambios.getNombreCompleto() != null && !cambios.getNombreCompleto().isBlank()) {
            existente.setNombreCompleto(cambios.getNombreCompleto());
        }

        if (cambios.getCorreo() != null && !cambios.getCorreo().isBlank()
                && !cambios.getCorreo().equalsIgnoreCase(existente.getCorreo())) {
            clienteRepository.findByCorreo(cambios.getCorreo())
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> { throw new RuntimeException("El correo ya esta registrado"); });
            existente.setCorreo(cambios.getCorreo());
        }

        if (cambios.getCelular() != null && !cambios.getCelular().isBlank()
                && !cambios.getCelular().equals(existente.getCelular())) {
            clienteRepository.findByCelular(cambios.getCelular())
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> { throw new RuntimeException("El numero de celular ya esta registrado"); });
            existente.setCelular(cambios.getCelular());
        }

        if (cambios.getContrasena() != null || cambios.getConfirmarContrasena() != null) {
            if (cambios.getContrasena() == null || cambios.getConfirmarContrasena() == null) {
                throw new IllegalArgumentException("Debe ingresar y confirmar la contrasena");
            }
            if (!cambios.getContrasena().equals(cambios.getConfirmarContrasena())) {
                throw new IllegalArgumentException("Las contrasenas no coinciden");
            }
            String contrasenaActual = cambios.getContrasenaActual();
            if (isBlank(contrasenaActual)) {
                throw new IllegalArgumentException("Debe ingresar su contrasena actual para modificarla");
            }
            if (!encoder.matches(contrasenaActual, existente.getContrasena())) {
                throw new IllegalArgumentException("La contrasena actual no es valida");
            }
            existente.setContrasena(encoder.encode(cambios.getContrasena()));
        }

        return clienteRepository.save(existente);
    }
}
