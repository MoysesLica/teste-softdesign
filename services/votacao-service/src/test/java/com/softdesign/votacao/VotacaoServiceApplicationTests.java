package com.softdesign.votacao;

import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.kafka.event.VotoSolicitadoEvent;
import com.softdesign.votacao.kafka.consumer.VotoProcessamentoService;
import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusPauta;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import com.softdesign.votacao.repository.VotoRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:votacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.listener.concurrency=1",
    "spring.kafka.consumer.group-id=votacao-service-integration-test",
    "kafka.voto.topic=voto-solicitado-integration-test",
    "kafka.voto.partitions=1",
    "kafka.voto.retry.attempts=3",
    "kafka.voto.retry.delay=100ms",
    "kafka.voto.retry.max-delay=100ms",
    "kafka.voto.retry.jitter=0ms",
    "kafka.voto.publicacao.intervalo=50ms",
    "kafka.voto.publicacao.lote=10"
})
@EmbeddedKafka(partitions = 1)
@DirtiesContext
@AutoConfigureMockMvc
class VotacaoServiceApplicationTests {

    private static final AtomicInteger CHAMADAS_CPF = new AtomicInteger();
    private static final HttpServer CPF_SERVER = iniciarCpfServer();
    private static volatile int cpfStatus = HttpStatus.OK.value();
    private static volatile String cpfResponse = "{\"status\":\"ABLE_TO_VOTE\"}";

    @Autowired
    private PautaRepository pautaRepository;
    @Autowired
    private VotoRepository votoRepository;
    @Autowired
    private VotoPublicacaoRepository votoPublicacaoRepository;
    @Autowired
    private KafkaTemplate<String, VotoSolicitadoEvent> kafkaTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.softdesign.votacao.service.v1.PautaService pautaService;
    @MockitoSpyBean
    private VotoProcessamentoService votoProcessamentoService;

    @DynamicPropertySource
    static void cpfProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "integrations.validacao-cpf.base-url",
            () -> "http://localhost:" + CPF_SERVER.getAddress().getPort()
        );
    }

    @BeforeEach
    void resetCpfServer() {
        CHAMADAS_CPF.set(0);
        cpfStatus = HttpStatus.OK.value();
        cpfResponse = "{\"status\":\"ABLE_TO_VOTE\"}";
    }

    @AfterAll
    static void finalizarCpfServer() {
        CPF_SERVER.stop(0);
    }

    // Verifica se o contexto completo da aplicação consegue subir com as dependências de teste.
    @Test
    void contextLoads() {
    }

    // Exercita o caminho inteiro da API v3, passando pela outbox, Kafka e validação do CPF.
    @Test
    void devePublicarEProcessarVotoCriadoPelaV3() throws Exception {
        Pauta pauta = salvarPautaAberta();
        String location = mockMvc.perform(post("/api/v3/votos")
                .contentType("application/json")
                .content("""
                    {
                      "cpf": "03425110250",
                      "opcao": "SIM",
                      "pautaId": "%s"
                    }
                    """.formatted(pauta.getId())))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.statusProcessamento").value("PENDENTE"))
            .andExpect(jsonPath("$.cpfValidado").value(false))
            .andExpect(jsonPath("$.aptoParaVotar").value(false))
            .andExpect(jsonPath("$.contabilizado").value(false))
            .andReturn()
            .getResponse()
            .getHeader("Location");

        assertNotNull(location);
        UUID votoId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        Voto votoProcessado = aguardarStatus(votoId, StatusProcessamentoVoto.CONTABILIZADO);
        assertTrue(votoProcessado.isCpfValidado());
        assertTrue(votoProcessado.isAptoParaVotar());
        assertTrue(votoProcessado.isContabilizado());
        aguardarOutboxVazia();
        mockMvc.perform(get("/api/v3/votos/{id}", votoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusProcessamento").value("CONTABILIZADO"))
            .andExpect(jsonPath("$.cpfValidado").value(true))
            .andExpect(jsonPath("$.aptoParaVotar").value(true))
            .andExpect(jsonPath("$.contabilizado").value(true));
    }

    // Garante que uma mensagem recebida diretamente pelo Kafka também atualiza o voto salvo.
    @Test
    void deveProcessarVotoPublicadoNoKafka() throws Exception {
        Voto voto = salvarVotoPendente();

        publicar(voto);

        Voto votoProcessado = aguardarStatus(voto.getId(), StatusProcessamentoVoto.CONTABILIZADO);
        assertTrue(votoProcessado.isCpfValidado());
        assertTrue(votoProcessado.isAptoParaVotar());
        assertTrue(votoProcessado.isContabilizado());
        assertEquals(1, CHAMADAS_CPF.get());
    }

    // Erro conhecido de negócio deve encerrar o processamento sem ocupar a fila de retry.
    @Test
    void naoDeveFazerRetryParaErroDeNegocio() throws Exception {
        cpfStatus = HttpStatus.BAD_REQUEST.value();
        cpfResponse = "";
        Voto voto = salvarVotoPendente();

        publicar(voto);

        Voto votoProcessado = aguardarStatus(voto.getId(), StatusProcessamentoVoto.CPF_INVALIDO);
        assertFalse(votoProcessado.isContabilizado());
        TimeUnit.MILLISECONDS.sleep(500);
        assertEquals(1, CHAMADAS_CPF.get());
    }

    // Indisponibilidade temporária do serviço de CPF deve consumir todas as tentativas configuradas.
    @Test
    void deveFazerRetryQuandoServicoCpfEstiverIndisponivel() throws Exception {
        cpfStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        cpfResponse = "";
        Voto voto = salvarVotoPendente();

        publicar(voto);

        Voto votoProcessado = aguardarStatus(voto.getId(), StatusProcessamentoVoto.SERVICO_CPF_INDISPONIVEL);
        assertFalse(votoProcessado.isContabilizado());
        assertEquals(3, CHAMADAS_CPF.get());
    }

    // Uma falha inesperada deve passar pelo retry e terminar identificada como erro de processamento.
    @Test
    void deveFazerRetryEFinalizarComoErroProcessamentoParaFalhaInesperada() throws Exception {
        Voto voto = salvarVotoPendente();
        doThrow(new IllegalStateException("Falha inesperada de teste"))
            .when(votoProcessamentoService)
            .buscarPendente(voto.getId());

        publicar(voto);

        Voto votoProcessado = aguardarStatus(voto.getId(), StatusProcessamentoVoto.ERRO_PROCESSAMENTO);
        assertFalse(votoProcessado.isContabilizado());
        verify(votoProcessamentoService, timeout(10_000).times(3)).buscarPendente(voto.getId());
    }

    // A pauta não pode ser fechada enquanto ainda existir voto aguardando processamento.
    @Test
    void naoDeveFecharPautaEnquantoExistirVotoPendente() {
        Voto voto = salvarVotoPendente();

        assertThrows(PreconditionFailedException.class, () -> pautaService.fecharVotacao(voto.getPauta().getId()));
    }

    // No fechamento, somente votos realmente contabilizados podem alterar o resultado e os totais.
    @Test
    void deveContarSomenteVotosContabilizadosAoFecharPauta() {
        Pauta pauta = salvarPautaAberta();
        Voto votoContabilizado = novoVoto(pauta, "03425110250", OpcaoVoto.SIM);
        votoRepository.save(votoContabilizado);
        Voto votoRejeitado = novoVoto(pauta, "00000000191", OpcaoVoto.NAO);
        votoRejeitado.setContabilizado(false);
        votoRejeitado.setStatusProcessamento(StatusProcessamentoVoto.CPF_INVALIDO);
        votoRepository.save(votoRejeitado);

        var response = pautaService.fecharVotacao(pauta.getId());

        assertEquals(StatusPauta.APROVADO, response.status());
        assertEquals(1, response.votosSim());
        assertEquals(0, response.votosNao());
    }

    private Voto salvarVotoPendente() {
        Pauta pauta = salvarPautaAberta();

        Voto voto = new Voto();
        voto.setPauta(pauta);
        voto.setCpf("03425110250");
        voto.setOpcao(OpcaoVoto.SIM);
        voto.setCpfValidado(false);
        voto.setAptoParaVotar(false);
        voto.setContabilizado(false);
        voto.setStatusProcessamento(StatusProcessamentoVoto.PENDENTE);
        return votoRepository.save(voto);
    }

    private Pauta salvarPautaAberta() {
        Pauta pauta = new Pauta();
        pauta.setTitulo("Pauta de teste");
        pauta.setDescricao("Descrição da pauta de teste");
        pauta.setCodigo(UUID.randomUUID().toString());
        pauta.setAbertoParaVotacao(true);
        pauta.setDataAbertura(ZonedDateTime.now().minusMinutes(1));
        pauta.setDataEncerramento(ZonedDateTime.now().plusMinutes(1));
        return pautaRepository.save(pauta);
    }

    private Voto novoVoto(Pauta pauta, String cpf, OpcaoVoto opcao) {
        Voto voto = new Voto();
        voto.setPauta(pauta);
        voto.setCpf(cpf);
        voto.setOpcao(opcao);
        return voto;
    }

    private void publicar(Voto voto) throws Exception {
        VotoSolicitadoEvent evento = new VotoSolicitadoEvent(voto.getId());
        kafkaTemplate.send("voto-solicitado-integration-test", voto.getId().toString(), evento)
            .get(5, TimeUnit.SECONDS);
    }

    private Voto aguardarStatus(UUID votoId, StatusProcessamentoVoto status) throws InterruptedException {
        ZonedDateTime limite = ZonedDateTime.now().plus(Duration.ofSeconds(10));
        while (ZonedDateTime.now().isBefore(limite)) {
            Voto voto = votoRepository.findById(votoId).orElseThrow();
            if (voto.getStatusProcessamento() == status)
                return voto;
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return fail("O voto não chegou ao status esperado: " + status);
    }

    private void aguardarOutboxVazia() throws InterruptedException {
        ZonedDateTime limite = ZonedDateTime.now().plus(Duration.ofSeconds(10));
        while (ZonedDateTime.now().isBefore(limite)) {
            if (votoPublicacaoRepository.count() == 0)
                return;
            TimeUnit.MILLISECONDS.sleep(50);
        }
        fail("A publicação do voto não foi removida da outbox");
    }

    private static HttpServer iniciarCpfServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/v1/cpf", VotacaoServiceApplicationTests::responderCpf);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível iniciar o servidor de CPF para os testes", exception);
        }
    }

    private static void responderCpf(HttpExchange exchange) throws IOException {
        CHAMADAS_CPF.incrementAndGet();
        byte[] body = cpfResponse.getBytes(StandardCharsets.UTF_8);
        if (body.length > 0)
            exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(cpfStatus, body.length);
        if (body.length > 0)
            exchange.getResponseBody().write(body);
        exchange.close();
    }

}
