package com.softdesign.votacao.kafka.producer;

import com.softdesign.votacao.kafka.event.VotoSolicitadoEvent;
import com.softdesign.votacao.model.VotoPublicacao;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class VotoPublicacaoService {

    private final VotoPublicacaoRepository votoPublicacaoRepository;
    private final VotoProducer votoProducer;

    @Value("${kafka.voto.publicacao.lote}")
    private int tamanhoLote;

    @Value("${kafka.voto.publicacao.timeout}")
    private long timeoutPublicacao;

    private final AtomicLong publicadosDesdeUltimaMetrica = new AtomicLong();
    private final AtomicLong falhasDesdeUltimaMetrica = new AtomicLong();

    @Scheduled(
        initialDelayString = "${kafka.voto.publicacao.intervalo}",
        fixedDelayString = "${kafka.voto.publicacao.intervalo}"
    )
    @Transactional
    public void publicarPendentes() {
        List<VotoPublicacao> publicacoes = votoPublicacaoRepository.buscarParaPublicacao(tamanhoLote);
        List<CompletableFuture<SendResult<String, VotoSolicitadoEvent>>> envios = new ArrayList<>();
        for (VotoPublicacao publicacao : publicacoes) {
            try {
                envios.add(votoProducer.enviar(new VotoSolicitadoEvent(publicacao.getVotoId())));
            } catch (RuntimeException exception) {
                falhasDesdeUltimaMetrica.incrementAndGet();
                envios.add(null);
                break;
            }
        }

        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutPublicacao);
        List<VotoPublicacao> publicacoesConcluidas = new ArrayList<>();
        for (int indice = 0; indice < envios.size(); indice++) {
            CompletableFuture<SendResult<String, VotoSolicitadoEvent>> envio = envios.get(indice);
            if (envio == null)
                continue;
            try {
                long tempoRestante = Math.max(0, limite - System.nanoTime());
                envio.get(tempoRestante, TimeUnit.NANOSECONDS);
                publicacoesConcluidas.add(publicacoes.get(indice));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException exception) {
                falhasDesdeUltimaMetrica.incrementAndGet();
            }
        }
        if (!publicacoesConcluidas.isEmpty()) {
            votoPublicacaoRepository.deleteAllInBatch(publicacoesConcluidas);
            publicadosDesdeUltimaMetrica.addAndGet(publicacoesConcluidas.size());
        }
    }

    @Scheduled(
        initialDelayString = "${kafka.voto.metricas.intervalo}",
        fixedRateString = "${kafka.voto.metricas.intervalo}"
    )
    @Transactional(readOnly = true)
    public void registrarMetricas() {
        long pendentes = votoPublicacaoRepository.count();
        long publicados = publicadosDesdeUltimaMetrica.getAndSet(0);
        long falhas = falhasDesdeUltimaMetrica.getAndSet(0);

        if (publicados > 0 || falhas > 0 || pendentes > 0) {
            log.info(
                "metric=votos_kafka publicados={} falhas={} outbox_pendentes={}",
                publicados,
                falhas,
                pendentes
            );
        }
    }

}
