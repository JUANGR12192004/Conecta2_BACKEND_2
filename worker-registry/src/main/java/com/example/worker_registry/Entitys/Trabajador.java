package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(
    name = "trabajadores",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"correo"}),
        @UniqueConstraint(columnNames = {"celular"})
    }
)
public class Trabajador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El correo electronico no tiene un formato valido")
    @Column(unique = true)
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
        message = "La contrasena debe incluir mayusculas, minusculas, numeros y un caracter especial"
    )
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasena;

    /**
     * Campo solo para lectura desde el request.
     * - No se persiste (Transient).
     * - Se valida manualmente en el servicio para evitar errores automaticos.
     */
    @Transient
    @JsonProperty(value = "confirmarContrasena", access = JsonProperty.Access.WRITE_ONLY)
    private String confirmarContrasena;

    @NotBlank(message = "El numero de celular es obligatorio")
    @Column(unique = true)
    private String celular;

    @Transient
    @JsonProperty(value = "contrasenaActual", access = JsonProperty.Access.WRITE_ONLY)
    private String contrasenaActual;

    @JsonProperty(value = "currentPassword", access = JsonProperty.Access.WRITE_ONLY)
    public void setCurrentPasswordAlias(String currentPassword) {
        this.contrasenaActual = currentPassword;
    }

    @NotNull(message = "El area de servicio es obligatoria")
    @Enumerated(EnumType.STRING)
    private CategoriaServicio areaServicio;

    private boolean activo = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getConfirmarContrasena() {
        return confirmarContrasena;
    }

    public void setConfirmarContrasena(String confirmarContrasena) {
        this.confirmarContrasena = confirmarContrasena;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getContrasenaActual() {
        return contrasenaActual;
    }

    public void setContrasenaActual(String contrasenaActual) {
        this.contrasenaActual = contrasenaActual;
    }

    public CategoriaServicio getAreaServicio() {
        return areaServicio;
    }

    public void setAreaServicio(CategoriaServicio areaServicio) {
        this.areaServicio = areaServicio;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
