package com.softdesign.votacao.kafka.producer;

import com.softdesign.votacao.kafka.event.VotoSolicitadoEvent;
import com.softdesign.votacao.model.VotoPublicacao;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VotoPublicacaoServiceTest {

    private VotoPublicacaoRepository votoPublicacaoRepository;
    private VotoProducer votoProducer;
    private VotoPublicacaoService votoPublicacaoService;

    @BeforeEach
    void setUp() {
        votoPublicacaoRepository = mock(VotoPublicacaoRepository.class);
        votoProducer = mock(VotoProducer.class);
        votoPublicacaoService = new VotoPublicacaoService(votoPublicacaoRepository, votoProducer);
        ReflectionTestUtils.setField(votoPublicacaoService, "tamanhoLote", 100);
        ReflectionTestUtils.setField(votoPublicacaoService, "timeoutPublicacao", 1000L);
    }

    // A outbox só deve ser limpa depois que o Kafka confirmar o envio da mensagem.
    @Test
    void deveRemoverPublicacaoDepoisDaConfirmacaoDoKafka() {
        VotoPublicacao publicacao = publicacao();
        when(votoPublicacaoRepository.buscarParaPublicacao(100)).thenReturn(List.of(publicacao));
        when(votoProducer.enviar(any(VotoSolicitadoEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        votoPublicacaoService.publicarPendentes();

        verify(votoPublicacaoRepository).deleteAllInBatch(List.of(publicacao));
    }

    // Se o envio assíncrono falhar, o registro fica guardado para uma tentativa futura.
    @Test
    void deveManterPublicacaoQuandoKafkaEstiverIndisponivel() {
        VotoPublicacao publicacao = publicacao();
        when(votoPublicacaoRepository.buscarParaPublicacao(100)).thenReturn(List.of(publicacao));
        when(votoProducer.enviar(any(VotoSolicitadoEvent.class)))
            .thenReturn(CompletableFuture.failedFuture(new KafkaException("Kafka indisponível")));

        votoPublicacaoService.publicarPendentes();

        verify(votoPublicacaoRepository, never()).deleteAllInBatch(anyList());
    }

    // Uma falha no meio do lote interrompe os próximos envios e remove apenas o que foi confirmado.
    @Test
    void deveInterromperLoteQuandoEnvioFalharAntesDoFuture() {
        VotoPublicacao primeira = publicacao();
        VotoPublicacao segunda = publicacao();
        VotoPublicacao terceira = publicacao();
        when(votoPublicacaoRepository.buscarParaPublicacao(100))
            .thenReturn(List.of(primeira, segunda, terceira));
        when(votoProducer.enviar(any(VotoSolicitadoEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(null))
            .thenThrow(new KafkaException("Kafka indisponível"));

        votoPublicacaoService.publicarPendentes();

        verify(votoProducer, times(2)).enviar(any(VotoSolicitadoEvent.class));
        verify(votoPublicacaoRepository).deleteAllInBatch(List.of(primeira));
    }

    private VotoPublicacao publicacao() {
        VotoPublicacao publicacao = new VotoPublicacao();
        publicacao.setId(UUID.randomUUID());
        publicacao.setVotoId(UUID.randomUUID());
        return publicacao;
    }

}
