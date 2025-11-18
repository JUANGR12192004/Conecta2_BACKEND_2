package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ServicioClienteServiceTest {

    private ServicioRepository servicioRepository;
    private ClienteRepository clienteRepository;
    private OfertaRepository ofertaRepository;
    private ServicioClienteService servicioClienteService;

    @BeforeEach
    void setUp() {
        servicioRepository = Mockito.mock(ServicioRepository.class);
        clienteRepository = Mockito.mock(ClienteRepository.class);
        ofertaRepository = Mockito.mock(OfertaRepository.class);
        servicioClienteService = new ServicioClienteService(servicioRepository, clienteRepository, ofertaRepository);
    }

    @Test
    void listarDisponibles_filtraExpiradosYActualizaEstado() {
        var vigente = Servicio.builder()
                .id(1L)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(2))
                .build();
        var expirado = Servicio.builder()
                .id(2L)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().minusDays(1))
                .build();

        when(servicioRepository.findByEstado(EstadoServicio.PENDIENTE))
                .thenReturn(List.of(vigente, expirado));

        var result = servicioClienteService.listarDisponibles();

        assertEquals(1, result.size());
        assertEquals(vigente, result.get(0));
        assertEquals(EstadoServicio.CANCELADO, expirado.getEstado());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Servicio>> captor = ArgumentCaptor.forClass(List.class);
        verify(servicioRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().contains(expirado));
    }

    @Test
    void listarPorCliente_actualizaEstadosVencidos() {
        Long clienteId = 5L;
        var vigente = Servicio.builder()
                .id(3L)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(3))
                .build();
        var expirado = Servicio.builder()
                .id(4L)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().minusDays(2))
                .build();

        var servicios = new ArrayList<>(List.of(vigente, expirado));
        when(servicioRepository.findByCliente_Id(clienteId)).thenReturn(servicios);

        var result = servicioClienteService.listarPorCliente(clienteId);

        assertEquals(2, result.size());
        assertEquals(EstadoServicio.CANCELADO, expirado.getEstado());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Servicio>> captor = ArgumentCaptor.forClass(List.class);
        verify(servicioRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().contains(expirado));
    }

    @Test
    void eliminarServicio_borraOfertasAntesDelServicio() {
        Long clienteId = 10L;
        Long servicioId = 200L;

        var cliente = Cliente.builder().id(clienteId).build();
        var servicio = Servicio.builder()
                .id(servicioId)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
                .build();

        when(servicioRepository.findByIdAndCliente_Id(servicioId, clienteId))
                .thenReturn(Optional.of(servicio));

        var resultado = servicioClienteService.eliminarServicio(clienteId, servicioId);

        assertTrue(resultado.exitoso());
        verify(ofertaRepository).deleteByServicio_Id(servicioId);
        verify(servicioRepository).delete(servicio);
    }
}
