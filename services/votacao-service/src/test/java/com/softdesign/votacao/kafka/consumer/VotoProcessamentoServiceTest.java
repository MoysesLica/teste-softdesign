package com.softdesign.votacao.kafka.consumer;

import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.VotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VotoProcessamentoServiceTest {

    private VotoRepository votoRepository;
    private VotoProcessamentoService votoProcessamentoService;

    @BeforeEach
    void setUp() {
        votoRepository = mock(VotoRepository.class);
        votoProcessamentoService = new VotoProcessamentoService(votoRepository);
    }

    // CPF válido e apto deve deixar todos os indicadores do voto como processados com sucesso.
    @Test
    void deveContabilizarVotoApto() {
        Voto voto = votoPendente();
        when(votoRepository.findByIdParaProcessamento(voto.getId())).thenReturn(Optional.of(voto));

        votoProcessamentoService.finalizar(voto.getId(), StatusProcessamentoVoto.CONTABILIZADO);

        assertTrue(voto.isCpfValidado());
        assertTrue(voto.isAptoParaVotar());
        assertTrue(voto.isContabilizado());
        assertEquals(StatusProcessamentoVoto.CONTABILIZADO, voto.getStatusProcessamento());
        verify(votoRepository).save(voto);
    }

    // O CPF pode ser válido sem estar apto, mas esse voto não entra na contagem da pauta.
    @Test
    void deveRegistrarCpfValidoNaoApto() {
        Voto voto = votoPendente();
        when(votoRepository.findByIdParaProcessamento(voto.getId())).thenReturn(Optional.of(voto));

        votoProcessamentoService.finalizar(voto.getId(), StatusProcessamentoVoto.NAO_APTO);

        assertTrue(voto.isCpfValidado());
        assertFalse(voto.isAptoParaVotar());
        assertFalse(voto.isContabilizado());
        assertEquals(StatusProcessamentoVoto.NAO_APTO, voto.getStatusProcessamento());
        verify(votoRepository).save(voto);
    }

    // Reentrega da mesma mensagem não pode alterar um voto que já chegou a um estado final.
    @Test
    void naoDeveProcessarNovamenteUmVotoFinalizado() {
        Voto voto = votoPendente();
        voto.setStatusProcessamento(StatusProcessamentoVoto.CPF_INVALIDO);
        when(votoRepository.findByIdParaProcessamento(voto.getId())).thenReturn(Optional.of(voto));

        votoProcessamentoService.finalizar(voto.getId(), StatusProcessamentoVoto.CONTABILIZADO);

        assertEquals(StatusProcessamentoVoto.CPF_INVALIDO, voto.getStatusProcessamento());
        verify(votoRepository, never()).save(any());
    }

    private Voto votoPendente() {
        Voto voto = new Voto();
        voto.setId(UUID.randomUUID());
        voto.setStatusProcessamento(StatusProcessamentoVoto.PENDENTE);
        voto.setContabilizado(false);
        return voto;
    }

}
