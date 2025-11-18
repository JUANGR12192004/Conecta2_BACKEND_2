package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Table(name = "ofertas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"servicio_id", "trabajador_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    @JsonIgnore
    private Servicio servicio;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id", nullable = false)
    @JsonIgnore
    private Trabajador trabajador;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    @Digits(integer = 12, fraction = 2, message = "El monto debe tener como maximo 2 decimales")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Size(max = 500, message = "El mensaje no puede exceder 500 caracteres")
    @Column(length = 500)
    private String mensaje;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @JsonIgnore
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNegociacion estado = EstadoNegociacion.EN_NEGOCIACION;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ultima_propuesta_por", nullable = false)
    private ParticipanteOferta ultimaPropuestaPor = ParticipanteOferta.TRABAJADOR;

    @Column(name = "monto_trabajador", precision = 14, scale = 2)
    private BigDecimal montoTrabajador;

    @Column(name = "monto_cliente", precision = 14, scale = 2)
    private BigDecimal montoCliente;

    @Column(name = "monto_acordado", precision = 14, scale = 2)
    private BigDecimal montoAcordado;

    @PrePersist
    void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = this.creadoEn;
    }

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("servicioId")
    public Long getServicioId() {
        return servicio != null ? servicio.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("trabajadorId")
    public Long getTrabajadorId() {
        return trabajador != null ? trabajador.getId() : null;
    }

    @JsonProperty("estadoNegociacion")
    public EstadoNegociacion getEstadoNegociacion() {
        return estado;
    }

    @JsonProperty("serviceId")
    public Long getServiceId() {
        return getServicioId();
    }

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "payment_client_secret")
    private String paymentClientSecret;

    @Column(name = "payment_status")
    private String paymentStatus;

    @JsonIgnore
    @Column(name = "payment_metadata", columnDefinition = "text")
    private String paymentMetadata;

    @JsonProperty("paymentIntentId")
    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    @JsonProperty("paymentClientSecret")
    public String getPaymentClientSecret() {
        return paymentClientSecret;
    }

    @JsonProperty("paymentStatus")
    public String getPaymentStatus() {
        return paymentStatus;
    }

    @JsonProperty("paymentMetadata")
    public String getPaymentMetadata() {
        return paymentMetadata;
    }
}
