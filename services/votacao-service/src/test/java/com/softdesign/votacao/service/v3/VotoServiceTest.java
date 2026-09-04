package com.softdesign.votacao.service.v3;

import com.softdesign.votacao.dto.voto.VotoAsyncResponse;
import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.mapper.VotoMapper;
import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.VotoPublicacao;
import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import com.softdesign.votacao.repository.VotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VotoServiceTest {

    @Mock
    private PautaRepository pautaRepository;
    @Mock
    private VotoRepository votoRepository;
    @Mock
    private VotoMapper votoMapper;
    @Mock
    private VotoPublicacaoRepository votoPublicacaoRepository;

    private VotoService votoService;

    @BeforeEach
    void setUp() {
        votoService = new VotoService(pautaRepository, votoRepository, votoPublicacaoRepository, votoMapper);
    }

    // Confere se voto e registro de publicação são criados juntos para o processamento assíncrono.
    @Test
    void deveSalvarVotoPendenteERegistrarPublicacao() {
        UUID pautaId = UUID.randomUUID();
        UUID votoId = UUID.randomUUID();
        VotoCreateRequest request = new VotoCreateRequest("03425110250", OpcaoVoto.SIM, pautaId);
        Pauta pauta = pautaAberta(pautaId);
        Voto voto = new Voto();
        VotoAsyncResponse response = new VotoAsyncResponse(
            votoId,
            OpcaoVoto.SIM,
            request.cpf(),
            false,
            false,
            false,
            StatusProcessamentoVoto.PENDENTE,
            null,
            null
        );

        when(pautaRepository.findByIdParaVoto(pautaId)).thenReturn(Optional.of(pauta));
        when(votoRepository.existsByPautaAndCpf(pauta, request.cpf())).thenReturn(false);
        when(votoMapper.toEntity(request)).thenReturn(voto);
        when(votoRepository.saveAndFlush(voto)).thenAnswer(_ -> {
            voto.setId(votoId);
            return voto;
        });
        when(votoMapper.toAsyncResponse(voto)).thenReturn(response);

        VotoAsyncResponse resultado = votoService.criar(request);

        assertSame(response, resultado);
        assertSame(pauta, voto.getPauta());
        assertFalse(voto.isCpfValidado());
        assertFalse(voto.isAptoParaVotar());
        assertFalse(voto.isContabilizado());
        assertEquals(StatusProcessamentoVoto.PENDENTE, voto.getStatusProcessamento());
        verify(votoPublicacaoRepository).save(argThat(publicacao -> votoId.equals(publicacao.getVotoId())));
    }

    // Um CPF que já votou na pauta deve ser recusado antes de qualquer nova gravação.
    @Test
    void naoDeveSalvarQuandoCpfJaTiverVotado() {
        UUID pautaId = UUID.randomUUID();
        VotoCreateRequest request = new VotoCreateRequest("03425110250", OpcaoVoto.SIM, pautaId);
        Pauta pauta = pautaAberta(pautaId);
        when(pautaRepository.findByIdParaVoto(pautaId)).thenReturn(Optional.of(pauta));
        when(votoRepository.existsByPautaAndCpf(pauta, request.cpf())).thenReturn(true);

        assertThrows(PreconditionFailedException.class, () -> votoService.criar(request));

        verify(votoRepository, never()).saveAndFlush(any());
        verify(votoPublicacaoRepository, never()).save(any(VotoPublicacao.class));
    }

    // Depois do prazo da sessão, nem o voto nem a publicação podem ser persistidos.
    @Test
    void naoDeveSalvarQuandoPrazoTiverEncerrado() {
        UUID pautaId = UUID.randomUUID();
        VotoCreateRequest request = new VotoCreateRequest("03425110250", OpcaoVoto.SIM, pautaId);
        Pauta pauta = pautaAberta(pautaId);
        pauta.setDataEncerramento(ZonedDateTime.now().minusSeconds(1));
        when(pautaRepository.findByIdParaVoto(pautaId)).thenReturn(Optional.of(pauta));
        when(votoRepository.existsByPautaAndCpf(pauta, request.cpf())).thenReturn(false);

        assertThrows(PreconditionFailedException.class, () -> votoService.criar(request));

        verify(votoRepository, never()).saveAndFlush(any());
        verify(votoPublicacaoRepository, never()).save(any(VotoPublicacao.class));
    }

    private Pauta pautaAberta(UUID id) {
        Pauta pauta = new Pauta();
        pauta.setId(id);
        pauta.setAbertoParaVotacao(true);
        pauta.setDataEncerramento(ZonedDateTime.now().plusMinutes(1));
        pauta.setAtivo(true);
        return pauta;
    }

}
