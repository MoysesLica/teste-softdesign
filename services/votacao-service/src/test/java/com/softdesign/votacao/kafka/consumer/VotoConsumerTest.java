package com.softdesign.votacao.kafka.consumer;

import com.softdesign.votacao.exception.ServicoValidacaoCpfIndisponivelException;
import com.softdesign.votacao.kafka.event.VotoSolicitadoEvent;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.*;

class VotoConsumerTest {

    private static final String CPF = "03425110250";

    private VotoProcessamentoService votoProcessamentoService;
    private VotoConsumer votoConsumer;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        votoProcessamentoService = mock(VotoProcessamentoService.class);
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://validacao-cpf");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        votoConsumer = new VotoConsumer(votoProcessamentoService, restClientBuilder.build());
    }

    // Resposta positiva do serviço de CPF deve liberar o voto para contabilização.
    @Test
    void deveContabilizarQuandoCpfEstiverApto() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(voto);
        server.expect(once(), requestTo("http://validacao-cpf/api/v1/cpf/" + CPF))
            .andRespond(withSuccess("{\"status\":\"ABLE_TO_VOTE\"}", MediaType.APPLICATION_JSON));

        votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId()));

        verify(votoProcessamentoService).finalizar(voto.getId(), StatusProcessamentoVoto.CONTABILIZADO);
        server.verify();
    }

    // Resposta de não apto encerra o fluxo normalmente, mas sem contabilizar o voto.
    @Test
    void naoDeveContabilizarQuandoCpfNaoEstiverApto() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(voto);
        server.expect(once(), requestTo("http://validacao-cpf/api/v1/cpf/" + CPF))
            .andRespond(withSuccess("{\"status\":\"UNABLE_TO_VOTE\"}", MediaType.APPLICATION_JSON));

        votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId()));

        verify(votoProcessamentoService).finalizar(voto.getId(), StatusProcessamentoVoto.NAO_APTO);
        server.verify();
    }

    // CPF inválido é erro definitivo de negócio e não deve provocar retry no Kafka.
    @Test
    void deveFinalizarSemRetryQuandoCpfForInvalido() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(voto);
        server.expect(once(), requestTo("http://validacao-cpf/api/v1/cpf/" + CPF))
            .andRespond(withStatus(BAD_REQUEST));

        assertDoesNotThrow(() -> votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId())));

        verify(votoProcessamentoService).finalizar(voto.getId(), StatusProcessamentoVoto.CPF_INVALIDO);
        server.verify();
    }

    // CPF não cadastrado também termina na primeira tentativa com seu status específico.
    @Test
    void deveFinalizarSemRetryQuandoCpfNaoForEncontrado() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(voto);
        server.expect(once(), requestTo("http://validacao-cpf/api/v1/cpf/" + CPF))
            .andRespond(withStatus(NOT_FOUND));

        assertDoesNotThrow(() -> votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId())));

        verify(votoProcessamentoService).finalizar(voto.getId(), StatusProcessamentoVoto.CPF_NAO_ENCONTRADO);
        server.verify();
    }

    // Indisponibilidade do serviço precisa lançar a exceção que alimenta o mecanismo de retry.
    @Test
    void deveSolicitarRetryQuandoServicoEstiverIndisponivel() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(voto);
        server.expect(once(), requestTo("http://validacao-cpf/api/v1/cpf/" + CPF))
            .andRespond(withStatus(SERVICE_UNAVAILABLE));

        assertThrows(
            ServicoValidacaoCpfIndisponivelException.class,
            () -> votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId()))
        );

        verify(votoProcessamentoService, never()).finalizar(any(), any());
        server.verify();
    }

    // Um erro interno retornado pela API é registrado sem repetir indefinidamente a mensagem.
    @Test
    void naoDeveFazerRetryParaErroInternoDaApi() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(voto);
        server.expect(once(), requestTo("http://validacao-cpf/api/v1/cpf/" + CPF))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertDoesNotThrow(() -> votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId())));

        verify(votoProcessamentoService).finalizar(voto.getId(), StatusProcessamentoVoto.ERRO_VALIDACAO_CPF);
        server.verify();
    }

    // Se outra entrega já concluiu o voto, a mensagem repetida pode ser descartada com segurança.
    @Test
    void deveIgnorarEventoQueJaFoiProcessado() {
        Voto voto = votoPendente();
        when(votoProcessamentoService.buscarPendente(voto.getId())).thenReturn(null);

        votoConsumer.consumir(new VotoSolicitadoEvent(voto.getId()));

        verify(votoProcessamentoService, never()).finalizar(any(), any());
        server.verify();
    }

    // Depois da última tentativa, a DLT deve registrar a indisponibilidade como estado final.
    @Test
    void deveMarcarIndisponibilidadeAposEsgotarRetries() {
        Voto voto = votoPendente();
        votoConsumer.processarFalha(
            new VotoSolicitadoEvent(voto.getId()),
            ServicoValidacaoCpfIndisponivelException.class.getName(),
            null
        );

        verify(votoProcessamentoService).finalizar(
            voto.getId(),
            StatusProcessamentoVoto.SERVICO_CPF_INDISPONIVEL
        );
    }

    // Falhas não relacionadas ao serviço de CPF precisam aparecer como erro de processamento.
    @Test
    void deveDiferenciarErroInesperadoNaDlt() {
        Voto voto = votoPendente();
        votoConsumer.processarFalha(
            new VotoSolicitadoEvent(voto.getId()),
            IllegalStateException.class.getName(),
            null
        );

        verify(votoProcessamentoService).finalizar(voto.getId(), StatusProcessamentoVoto.ERRO_PROCESSAMENTO);
    }

    private Voto votoPendente() {
        Voto voto = new Voto();
        voto.setId(UUID.randomUUID());
        voto.setCpf(CPF);
        voto.setCpfValidado(false);
        voto.setAptoParaVotar(false);
        voto.setContabilizado(false);
        voto.setStatusProcessamento(StatusProcessamentoVoto.PENDENTE);
        return voto;
    }

}
