package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Services.RegistroCliente;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientAccountController {

    private final RegistroCliente registroCliente;

    public ClientAccountController(RegistroCliente registroCliente) {
        this.registroCliente = registroCliente;
    }

    @GetMapping("/me")
    public ResponseEntity<ClienteAccountDto> obtenerPerfilPropio(
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long clientId = requireClient(user, null);
        var cliente = registroCliente.obtenerClientePorId(clientId);
        return ResponseEntity.ok(ClienteAccountDto.from(cliente));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ClienteAccountDto> obtenerPerfilPorId(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        Long clientId = requireClient(user, id);
        var cliente = registroCliente.obtenerClientePorId(clientId);
        return ResponseEntity.ok(ClienteAccountDto.from(cliente));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<ClienteAccountDto> actualizarPerfil(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody Cliente cambios) {
        Long clientId = requireClient(user, id);
        var actualizado = registroCliente.actualizarCliente(clientId, cambios);
        return ResponseEntity.ok(ClienteAccountDto.from(actualizado));
    }

    private Long requireClient(AuthenticatedUser user, Long expectedId) {
        if (user == null || !user.hasRole("CLIENT")) {
            throw new AccessDeniedException("Rol de cliente requerido");
        }
        Long userId = user.userId();
        if (expectedId != null && !expectedId.equals(userId)) {
            throw new AccessDeniedException("No puede gestionar otra cuenta");
        }
        return userId;
    }

    public record ClienteAccountDto(Long id,
                                    String nombreCompleto,
                                    String correo,
                                    String celular,
                                    boolean activo) {
        public static ClienteAccountDto from(Cliente cliente) {
            return new ClienteAccountDto(
                    cliente.getId(),
                    cliente.getNombreCompleto(),
                    cliente.getCorreo(),
                    cliente.getCelular(),
                    cliente.isActivo()
            );
        }
    }
}
