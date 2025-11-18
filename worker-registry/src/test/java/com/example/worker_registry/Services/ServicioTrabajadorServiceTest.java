package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.CategoriaServicio;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Repository.TrabajadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioTrabajadorServiceTest {

    private ServicioRepository servicioRepository;
    private TrabajadorRepository trabajadorRepository;
    private OfertaRepository ofertaRepository;
    private OfertaService ofertaService;
    private ServicioTrabajadorService servicioTrabajadorService;

    @BeforeEach
    void setUp() {
        servicioRepository = Mockito.mock(ServicioRepository.class);
        trabajadorRepository = Mockito.mock(TrabajadorRepository.class);
        ofertaRepository = Mockito.mock(OfertaRepository.class);
        ofertaService = Mockito.mock(OfertaService.class);
        servicioTrabajadorService = new ServicioTrabajadorService(servicioRepository, trabajadorRepository, ofertaRepository, ofertaService);
    }

    @Test
    void listarDisponiblesPorArea_filtraServiciosExpirados() {
        Long trabajadorId = 1L;
        var trabajador = new Trabajador();
        trabajador.setId(trabajadorId);
        trabajador.setAreaServicio(CategoriaServicio.ASEO);

        when(trabajadorRepository.findById(trabajadorId)).thenReturn(Optional.of(trabajador));

        var vigente = Servicio.builder()
                .id(10L)
                .estado(EstadoServicio.PENDIENTE)
                .categoria(CategoriaServicio.ASEO)
                .fechaEstimada(LocalDateTime.now().plusDays(1))
                .build();
        var expirado = Servicio.builder()
                .id(11L)
                .estado(EstadoServicio.PENDIENTE)
                .categoria(CategoriaServicio.ASEO)
                .fechaEstimada(LocalDateTime.now().minusDays(1))
                .build();

        when(servicioRepository.findByEstadoAndCategoria(EstadoServicio.PENDIENTE, CategoriaServicio.ASEO))
                .thenReturn(List.of(vigente, expirado));

        var result = servicioTrabajadorService.listarDisponiblesPorArea(trabajadorId);

        assertEquals(1, result.size());
        assertEquals(vigente, result.get(0));
        assertEquals(EstadoServicio.CANCELADO, expirado.getEstado());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Servicio>> captor = ArgumentCaptor.forClass(List.class);
        verify(servicioRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().contains(expirado));
    }

    @Test
    void crearOferta_fallaSiServicioExpirado() {
        Long trabajadorId = 5L;
        Long servicioId = 50L;
        var trabajador = new Trabajador();
        trabajador.setId(trabajadorId);
        trabajador.setAreaServicio(CategoriaServicio.TECNOLOGIA);

        var servicio = Servicio.builder()
                .id(servicioId)
                .estado(EstadoServicio.PENDIENTE)
                .categoria(CategoriaServicio.TECNOLOGIA)
                .fechaEstimada(LocalDateTime.now().minusDays(2))
                .build();

        when(trabajadorRepository.findById(trabajadorId)).thenReturn(Optional.of(trabajador));
        when(servicioRepository.findById(servicioId)).thenReturn(Optional.of(servicio));
        when(ofertaRepository.findByServicio_IdAndTrabajador_Id(servicioId, trabajadorId)).thenReturn(Optional.empty());
        when(ofertaRepository.findFirstByServicio_IdAndEstado(servicioId, EstadoNegociacion.EN_NEGOCIACION))
                .thenReturn(Optional.empty());

        var data = new ServicioTrabajadorService.CrearOferta();
        data.monto = BigDecimal.TEN;
        data.mensaje = "oferta";

        assertThrows(IllegalStateException.class,
                () -> servicioTrabajadorService.crearOferta(trabajadorId, servicioId, data));

        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        verify(servicioRepository).save(servicio);
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }
}
