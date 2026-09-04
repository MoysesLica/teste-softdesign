package com.softdesign.votacao.kafka.consumer;

import com.softdesign.contracts.cpf.CpfValidationResponse;
import com.softdesign.contracts.cpf.CpfValidationStatus;
import com.softdesign.votacao.exception.ServicoValidacaoCpfIndisponivelException;
import com.softdesign.votacao.kafka.event.VotoSolicitadoEvent;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class VotoConsumer {

    private final VotoProcessamentoService votoProcessamentoService;
    private final RestClient cpfValidationRestClient;

    @RetryableTopic(
        attempts = "${kafka.voto.retry.attempts}",
        backOff = @BackOff(
            delayString = "${kafka.voto.retry.delay}",
            multiplierString = "${kafka.voto.retry.multiplier}",
            maxDelayString = "${kafka.voto.retry.max-delay}",
            jitterString = "${kafka.voto.retry.jitter}"
        ),
        include = Exception.class,
        traversingCauses = "true",
        numPartitions = "${kafka.voto.partitions}",
        replicationFactor = "1",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
        topics = "${kafka.voto.topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumir(
        VotoSolicitadoEvent evento,
        @Header(name = RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, required = false) Integer tentativa
    ) {
        try {
            processar(evento);
        } catch (RuntimeException exception) {
            StatusProcessamentoVoto status = exception instanceof ServicoValidacaoCpfIndisponivelException
                ? StatusProcessamentoVoto.SERVICO_CPF_INDISPONIVEL
                : StatusProcessamentoVoto.ERRO_PROCESSAMENTO;
            log.error(
                "metric=voto_retry voto_id={} status={} tentativa={} causa={}",
                evento.votoId(),
                status,
                tentativa != null ? tentativa : 1,
                exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    public void consumir(VotoSolicitadoEvent evento) {
        processar(evento);
    }

    private void processar(VotoSolicitadoEvent evento) {
        Voto voto = votoProcessamentoService.buscarPendente(evento.votoId());
        if (voto == null)
            return;

        StatusProcessamentoVoto status = validarCpf(voto.getCpf());
        votoProcessamentoService.finalizar(voto.getId(), status);
    }

    @DltHandler
    public void processarFalha(
        VotoSolicitadoEvent evento,
        @Header(name = KafkaHeaders.EXCEPTION_FQCN, required = false) String excecao,
        @Header(name = KafkaHeaders.EXCEPTION_CAUSE_FQCN, required = false) String causa
    ) {
        boolean servicoCpfIndisponivel = ServicoValidacaoCpfIndisponivelException.class.getName().equals(excecao)
            || ServicoValidacaoCpfIndisponivelException.class.getName().equals(causa);
        StatusProcessamentoVoto status = servicoCpfIndisponivel
            ? StatusProcessamentoVoto.SERVICO_CPF_INDISPONIVEL
            : StatusProcessamentoVoto.ERRO_PROCESSAMENTO;
        votoProcessamentoService.finalizar(
            evento.votoId(),
            status
        );
        log.error(
            "metric=voto_dlt voto_id={} status={} causa={}",
            evento.votoId(),
            status,
            nomeSimples(causa != null ? causa : excecao)
        );
    }

    private String nomeSimples(String nomeClasse) {
        if (nomeClasse == null || nomeClasse.isBlank())
            return "desconhecida";
        return nomeClasse.substring(nomeClasse.lastIndexOf('.') + 1);
    }

    private StatusProcessamentoVoto validarCpf(String cpf) {
        try {
            CpfValidationResponse response = cpfValidationRestClient.get()
                .uri("/api/v1/cpf/{cpf}", cpf)
                .retrieve()
                .body(CpfValidationResponse.class);

            if (response == null || response.status() == null)
                return StatusProcessamentoVoto.ERRO_VALIDACAO_CPF;
            return response.status() == CpfValidationStatus.ABLE_TO_VOTE
                ? StatusProcessamentoVoto.CONTABILIZADO
                : StatusProcessamentoVoto.NAO_APTO;
        } catch (HttpClientErrorException.BadRequest _) {
            return StatusProcessamentoVoto.CPF_INVALIDO;
        } catch (HttpClientErrorException.NotFound _) {
            return StatusProcessamentoVoto.CPF_NAO_ENCONTRADO;
        } catch (
            HttpServerErrorException.BadGateway |
            HttpServerErrorException.ServiceUnavailable |
            HttpServerErrorException.GatewayTimeout |
            ResourceAccessException exception
        ) {
            throw new ServicoValidacaoCpfIndisponivelException("O serviço de validação de CPF está indisponível", exception);
        } catch (RestClientException _) {
            return StatusProcessamentoVoto.ERRO_VALIDACAO_CPF;
        }
    }

}
