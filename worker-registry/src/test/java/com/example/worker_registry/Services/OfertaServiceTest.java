package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OfertaServiceTest {

    private OfertaRepository ofertaRepository;
    private ServicioRepository servicioRepository;
    private PushNotificationService pushNotificationService;
    private OfertaService ofertaService;

    @BeforeEach
    void setUp() {
        ofertaRepository = Mockito.mock(OfertaRepository.class);
        servicioRepository = Mockito.mock(ServicioRepository.class);
        pushNotificationService = Mockito.mock(PushNotificationService.class);
        ofertaService = new OfertaService(ofertaRepository, servicioRepository, pushNotificationService);
    }

    @Test
    void contraOfertaTrabajador_actualizaMontoYTurno() {
        var cliente = Cliente.builder().id(1L).build();
        var servicio = Servicio.builder()
                .id(10L)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(2))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(99L);

        var oferta = Oferta.builder()
                .id(5L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.CLIENTE)
                .monto(BigDecimal.valueOf(100))
                .build();

        when(ofertaRepository.findById(5L)).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));

        var contra = new OfertaService.ContraOferta();
        contra.monto = BigDecimal.valueOf(150);
        contra.mensaje = "subo el monto";

        var result = ofertaService.contraOfertaTrabajador(99L, 5L, contra);

        assertEquals(BigDecimal.valueOf(150), result.getMonto());
        assertEquals(ParticipanteOferta.TRABAJADOR, result.getUltimaPropuestaPor());
        assertEquals(BigDecimal.valueOf(150), result.getMontoTrabajador());
        assertEquals("subo el monto", result.getMensaje());
        verify(ofertaRepository).save(oferta);
    }

    @Test
    void contraOfertaTrabajador_fallaSiNoEsTurnoDelTrabajador() {
        var servicio = Servicio.builder()
                .id(11L)
                .cliente(Cliente.builder().id(2L).build())
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(1))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(7L);

        var oferta = Oferta.builder()
                .id(6L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .build();

        when(ofertaRepository.findById(6L)).thenReturn(Optional.of(oferta));

        var contra = new OfertaService.ContraOferta();
        contra.monto = BigDecimal.TEN;

        assertThrows(IllegalStateException.class,
                () -> ofertaService.contraOfertaTrabajador(7L, 6L, contra));
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }

    @Test
    void responderOfertaAceptada_cambiaEstadoDeServicio() {
        var cliente = Cliente.builder().id(3L).build();
        var servicio = Servicio.builder()
                .id(20L)
                .cliente(cliente)
                .titulo("Servicio Demo")
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(1))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(4L);

        var oferta = Oferta.builder()
                .id(8L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .monto(BigDecimal.valueOf(80))
                .build();

        when(ofertaRepository.findById(8L)).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(inv -> inv.getArgument(0));

        var resultado = ofertaService.responderOferta(3L, 8L, "ACCEPT");

        assertTrue(resultado.accepted());
        assertEquals("Oferta aceptada", resultado.mensaje());
        assertEquals(EstadoNegociacion.PENDIENTE_DE_PAGO, oferta.getEstado());
        assertEquals(EstadoServicio.PENDIENTE_PAGO, servicio.getEstado());
        assertEquals(trabajador.getId(), servicio.getAssignedWorkerId());

        verify(servicioRepository).save(servicio);
        verify(ofertaRepository).save(oferta);
        verify(pushNotificationService).notifyTrabajador(eq(trabajador.getId()), eq("Cliente aceptó la oferta"), eq("El cliente aceptó la oferta. El cliente debe realizar el pago."));
    }

    @Test
    void responderOfertaLanzaErrorCuandoServicioExpirado() {
        var cliente = Cliente.builder().id(40L).build();
        var servicio = Servicio.builder()
                .id(30L)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().minusDays(1))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(12L);

        var oferta = Oferta.builder()
                .id(15L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .monto(BigDecimal.valueOf(50))
                .build();

        when(ofertaRepository.findById(15L)).thenReturn(Optional.of(oferta));

        var body = new OfertaService.ResponderOferta();
        body.accept = true;

        assertThrows(IllegalStateException.class,
                () -> ofertaService.responderOferta(cliente.getId(), 15L, body));

        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        verify(servicioRepository).save(servicio);
        verify(ofertaRepository, never()).save(oferta);
    }

    @Test
    void responderOfertaTrabajadorAceptada_notificaCliente() {
        var cliente = Cliente.builder().id(50L).build();
        var servicio = Servicio.builder()
                .id(60L)
                .cliente(cliente)
                .titulo("Servicio Demo II")
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(2))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(77L);

        var oferta = Oferta.builder()
                .id(21L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.CLIENTE)
                .monto(BigDecimal.valueOf(120))
                .build();

        when(ofertaRepository.findById(21L)).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(inv -> inv.getArgument(0));

        var resultado = ofertaService.responderOfertaTrabajador(77L, 21L, "ACCEPT");

        assertTrue(resultado.accepted());
        assertEquals(EstadoServicio.PENDIENTE_PAGO, servicio.getEstado());
        assertEquals(EstadoNegociacion.PENDIENTE_DE_PAGO, oferta.getEstado());

        verify(servicioRepository).save(servicio);
        verify(ofertaRepository).save(oferta);
        verify(pushNotificationService).notifyCliente(eq(cliente.getId()), eq("Trabajador aceptó la oferta"), eq("El trabajador aceptó tu oferta. Debes proceder con el pago."));
    }
}
