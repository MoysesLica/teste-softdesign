package com.softdesign.votacao;

import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.mapper.VotoMapper;
import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.VotoPublicacao;
import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import com.softdesign.votacao.repository.VotoRepository;
import com.softdesign.votacao.service.v3.VotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(VotoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresPersistenceIntegrationTest {

    private static final String CPF = "03425110250";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("votacao_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private PautaRepository pautaRepository;
    @Autowired
    private VotoRepository votoRepository;
    @Autowired
    private VotoPublicacaoRepository votoPublicacaoRepository;
    @Autowired
    private VotoService votoService;
    @MockitoBean
    private VotoMapper votoMapper;

    @BeforeEach
    void limparBanco() {
        votoPublicacaoRepository.deleteAll();
        votoRepository.deleteAll();
        pautaRepository.deleteAll();
    }

    // Usa outra conexão para confirmar que pauta, voto e outbox chegaram de fato ao PostgreSQL.
    @Test
    void devePersistirPautaVotoEOutboxEmUmaNovaConexaoPostgresql() throws Exception {
        Pauta pauta = salvarPautaAberta();
        Voto voto = novoVoto(pauta, CPF);
        voto = votoRepository.saveAndFlush(voto);
        VotoPublicacao publicacao = new VotoPublicacao();
        publicacao.setVotoId(voto.getId());
        votoPublicacaoRepository.saveAndFlush(publicacao);

        try (var conexao = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var consulta = conexao.createStatement()) {
            assertEquals(1, quantidade(consulta, "pauta"));
            assertEquals(1, quantidade(consulta, "voto"));
            assertEquals(1, quantidade(consulta, "voto_publicacao"));
        }
    }

    // A restrição do banco é a última proteção contra dois votos do mesmo CPF na mesma pauta.
    @Test
    void deveAplicarConstraintDeUmVotoPorCpfEPautaNoPostgresql() {
        Pauta pauta = salvarPautaAberta();
        votoRepository.saveAndFlush(novoVoto(pauta, CPF));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> votoRepository.saveAndFlush(novoVoto(pauta, CPF))
        );
        assertEquals(1, votoRepository.count());
    }

    // Simula a corrida entre duas requisições iguais e espera apenas uma gravação vencedora.
    @Test
    void deveAceitarSomenteUmDeDoisVotosConcorrentesIguais() throws Exception {
        Pauta pauta = salvarPautaAberta();
        VotoCreateRequest request = new VotoCreateRequest(CPF, OpcaoVoto.SIM, pauta.getId());
        when(votoMapper.toEntity(any(VotoCreateRequest.class))).thenAnswer(_ -> {
            Voto voto = new Voto();
            voto.setCpf(CPF);
            voto.setOpcao(OpcaoVoto.SIM);
            return voto;
        });

        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tarefas = List.of(
                executor.submit(() -> criarConcorrentemente(request, inicio)),
                executor.submit(() -> criarConcorrentemente(request, inicio))
            );
            inicio.countDown();
            List<String> resultados = tarefas.stream().map(futuro -> {
                try {
                    return futuro.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertEquals(1, resultados.stream().filter("CRIADO"::equals).count());
            assertEquals(1, resultados.stream().filter("DUPLICADO"::equals).count());
        }
        assertEquals(1, votoRepository.count());
        assertEquals(1, votoPublicacaoRepository.count());
    }

    private String criarConcorrentemente(VotoCreateRequest request, CountDownLatch inicio) {
        try {
            inicio.await();
            votoService.criar(request);
            return "CRIADO";
        } catch (PreconditionFailedException exception) {
            return "DUPLICADO";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private long quantidade(java.sql.Statement consulta, String tabela) throws Exception {
        try (var resultado = consulta.executeQuery("select count(*) from " + tabela)) {
            assertTrue(resultado.next());
            return resultado.getLong(1);
        }
    }

    private Pauta salvarPautaAberta() {
        Pauta pauta = new Pauta();
        pauta.setTitulo("Pauta PostgreSQL");
        pauta.setDescricao("Teste isolado com Testcontainers");
        pauta.setCodigo(UUID.randomUUID().toString());
        pauta.setAbertoParaVotacao(true);
        pauta.setDataAbertura(ZonedDateTime.now().minusMinutes(1));
        pauta.setDataEncerramento(ZonedDateTime.now().plusMinutes(5));
        return pautaRepository.saveAndFlush(pauta);
    }

    private Voto novoVoto(Pauta pauta, String cpf) {
        Voto voto = new Voto();
        voto.setPauta(pauta);
        voto.setCpf(cpf);
        voto.setOpcao(OpcaoVoto.SIM);
        return voto;
    }
}
