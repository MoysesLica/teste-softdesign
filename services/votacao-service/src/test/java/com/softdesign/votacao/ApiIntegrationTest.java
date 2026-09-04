package com.softdesign.votacao;

import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import com.softdesign.votacao.repository.VotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:votacao-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.listener.concurrency=1",
    "kafka.voto.topic=voto-solicitado-api-test",
    "kafka.voto.partitions=1",
    "kafka.voto.publicacao.intervalo=1h",
    "integrations.validacao-cpf.base-url=http://localhost:1"
})
class ApiIntegrationTest {

    private static final String CPF = "03425110250";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PautaRepository pautaRepository;
    @Autowired
    private VotoRepository votoRepository;
    @Autowired
    private VotoPublicacaoRepository votoPublicacaoRepository;

    @BeforeEach
    void limparBanco() {
        votoPublicacaoRepository.deleteAll();
        votoRepository.deleteAll();
        pautaRepository.deleteAll();
    }

    // Percorre o ciclo básico da pauta e confirma que a inativação afeta a listagem.
    @Test
    void deveExecutarCadastroConsultaAtualizacaoListagemEInativacaoDaPauta() throws Exception {
        mockMvc.perform(post("/api/v1/pautas")
                .contentType("application/json")
                .content("""
                    {"titulo":"Assembleia","descricao":"Eleger conselho"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.titulo").value("Assembleia"))
            .andExpect(jsonPath("$.status").value("PENDENTE"));

        Pauta pauta = pautaRepository.findAll().getFirst();
        mockMvc.perform(get("/api/v1/pautas/{id}", pauta.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pauta.getId().toString()));

        mockMvc.perform(put("/api/v1/pautas/{id}", pauta.getId())
                .contentType("application/json")
                .content("""
                    {"titulo":"Assembleia atualizada","descricao":"Nova descrição"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Assembleia atualizada"));

        mockMvc.perform(get("/api/v1/pautas").param("ativo", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(delete("/api/v1/pautas/{id}", pauta.getId()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/pautas").param("ativo", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // Entradas inválidas e identificadores desconhecidos devem seguir o contrato de erro da API.
    @Test
    void deveValidarDadosDaPautaERecursosInexistentes() throws Exception {
        mockMvc.perform(post("/api/v1/pautas")
                .contentType("application/json")
                .content("{\"titulo\":\"\",\"descricao\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.errors", hasSize(2)));

        mockMvc.perform(get("/api/v1/pautas/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    // Sem data informada, a regra de negócio deve abrir a votação por aproximadamente um minuto.
    @Test
    void deveAbrirPautaPorUmMinutoQuandoEncerramentoNaoForInformado() throws Exception {
        Pauta pauta = salvarPauta(false, ZonedDateTime.now().plusHours(1));
        ZonedDateTime antes = ZonedDateTime.now();

        mockMvc.perform(post("/api/v1/pautas/{id}/open", pauta.getId())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.abertoParaVotacao").value(true));

        Pauta aberta = pautaRepository.findById(pauta.getId()).orElseThrow();
        assertTrue(aberta.getDataEncerramento().isAfter(antes.plusSeconds(55)));
        assertTrue(aberta.getDataEncerramento().isBefore(antes.plusSeconds(65)));

        mockMvc.perform(post("/api/v1/pautas/{id}/open", pauta.getId())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isPreconditionFailed());
    }

    // Uma data futura deve ser respeitada, enquanto uma data já vencida precisa ser rejeitada.
    @Test
    void deveRespeitarEncerramentoInformadoERejeitarDataPassada() throws Exception {
        Pauta pauta = salvarPauta(false, ZonedDateTime.now().plusHours(1));
        ZonedDateTime encerramento = ZonedDateTime.now().plusMinutes(10).withNano(0);

        mockMvc.perform(post("/api/v1/pautas/{id}/open", pauta.getId())
                .contentType("application/json")
                .content("{\"dataEncerramento\":\"%s\"}".formatted(encerramento)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataEncerramento").exists());
        assertEquals(
            encerramento.toInstant(),
            pautaRepository.findById(pauta.getId()).orElseThrow().getDataEncerramento().toInstant()
        );

        Pauta outra = salvarPauta(false, ZonedDateTime.now().plusHours(1));
        mockMvc.perform(post("/api/v1/pautas/{id}/open", outra.getId())
                .contentType("application/json")
                .content("{\"dataEncerramento\":\"%s\"}".formatted(ZonedDateTime.now().minusMinutes(1))))
            .andExpect(status().isBadRequest());
    }

    // Cobre os resultados possíveis e a regra adotada para empate ou pauta sem votos.
    @Test
    void deveFecharPautaComResultadoAprovadoReprovadoOuEmpatadoComoReprovado() throws Exception {
        Pauta aprovada = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        salvarVoto(aprovada, CPF, OpcaoVoto.SIM);
        salvarVoto(aprovada, "52998224725", OpcaoVoto.NAO);
        salvarVoto(aprovada, "11144477735", OpcaoVoto.SIM);
        mockMvc.perform(post("/api/v1/pautas/{id}/close", aprovada.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.votosSim").value(2))
            .andExpect(jsonPath("$.votosNao").value(1))
            .andExpect(jsonPath("$.fechada").value(true));
        mockMvc.perform(post("/api/v1/pautas/{id}/open", aprovada.getId())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isPreconditionFailed());

        Pauta reprovada = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        salvarVoto(reprovada, CPF, OpcaoVoto.NAO);
        salvarVoto(reprovada, "52998224725", OpcaoVoto.NAO);
        salvarVoto(reprovada, "11144477735", OpcaoVoto.SIM);
        mockMvc.perform(post("/api/v1/pautas/{id}/close", reprovada.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPROVADO"));

        Pauta empatada = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        salvarVoto(empatada, CPF, OpcaoVoto.SIM);
        salvarVoto(empatada, "52998224725", OpcaoVoto.NAO);
        mockMvc.perform(post("/api/v1/pautas/{id}/close", empatada.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPROVADO"));

        Pauta semVotos = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        mockMvc.perform(post("/api/v1/pautas/{id}/close", semVotos.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.votosSim").value(0))
            .andExpect(jsonPath("$.votosNao").value(0))
            .andExpect(jsonPath("$.status").value("REPROVADO"));
    }

    // Confere os principais códigos HTTP para payload inválido e recursos não encontrados.
    @Test
    void deveValidarContratoHttpDoVoto() throws Exception {
        Pauta pauta = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));

        mockMvc.perform(post("/api/v1/votos")
                .contentType("application/json")
                .content("{\"cpf\":\"123\",\"opcao\":\"TALVEZ\",\"pautaId\":\"%s\"}".formatted(pauta.getId())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/votos")
                .contentType("application/json")
                .content("{\"cpf\":\"%s\",\"opcao\":\"SIM\",\"pautaId\":\"%s\"}".formatted(CPF, UUID.randomUUID())))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v3/votos/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    // Os filtros podem ser usados separados ou combinados sem trazer votos de outra categoria.
    @Test
    void deveFiltrarVotosPorStatusProcessamentoEOpcao() throws Exception {
        Pauta pauta = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        salvarVoto(pauta, CPF, OpcaoVoto.SIM);

        Voto votoNao = new Voto();
        votoNao.setPauta(pauta);
        votoNao.setCpf("52998224725");
        votoNao.setOpcao(OpcaoVoto.NAO);
        votoNao.setStatusProcessamento(StatusProcessamentoVoto.CPF_NAO_ENCONTRADO);
        votoNao.setContabilizado(false);
        votoRepository.save(votoNao);

        mockMvc.perform(get("/api/v1/votos/{pautaId}", pauta.getId())
                .param("opcao", "SIM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].opcao").value("SIM"));

        mockMvc.perform(get("/api/v1/votos/{pautaId}", pauta.getId())
                .param("status_processamento", "CPF_NAO_ENCONTRADO")
                .param("opcao", "NAO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].statusProcessamento").value("CPF_NAO_ENCONTRADO"))
            .andExpect(jsonPath("$.content[0].opcao").value("NAO"));
    }

    // Reúne as travas de período, atividade e voto único por CPF dentro de cada pauta.
    @Test
    void deveAplicarRegrasDePeriodoAtividadeEDuplicidadeDoVoto() throws Exception {
        Pauta naoAberta = salvarPauta(false, ZonedDateTime.now().plusMinutes(5));
        votar(naoAberta, CPF).andExpect(status().isPreconditionFailed());

        Pauta expirada = salvarPauta(true, ZonedDateTime.now().minusSeconds(1));
        votar(expirada, CPF).andExpect(status().isPreconditionFailed());

        Pauta inativa = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        inativa.setAtivo(false);
        pautaRepository.save(inativa);
        votar(inativa, CPF).andExpect(status().isPreconditionFailed());

        Pauta primeira = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        votar(primeira, CPF).andExpect(status().isCreated());
        votar(primeira, CPF).andExpect(status().isPreconditionFailed());

        Pauta segunda = salvarPauta(true, ZonedDateTime.now().plusMinutes(5));
        votar(segunda, CPF).andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions votar(Pauta pauta, String cpf) throws Exception {
        return mockMvc.perform(post("/api/v1/votos")
            .contentType("application/json")
            .content("{\"cpf\":\"%s\",\"opcao\":\"SIM\",\"pautaId\":\"%s\"}".formatted(cpf, pauta.getId())));
    }

    private Pauta salvarPauta(boolean aberta, ZonedDateTime encerramento) {
        Pauta pauta = new Pauta();
        pauta.setTitulo("Pauta " + UUID.randomUUID());
        pauta.setDescricao("Descrição de teste");
        pauta.setCodigo(UUID.randomUUID().toString());
        pauta.setAbertoParaVotacao(aberta);
        if (aberta)
            pauta.setDataAbertura(ZonedDateTime.now().minusMinutes(1));
        pauta.setDataEncerramento(encerramento);
        return pautaRepository.save(pauta);
    }

    private void salvarVoto(Pauta pauta, String cpf, OpcaoVoto opcao) {
        Voto voto = new Voto();
        voto.setPauta(pauta);
        voto.setCpf(cpf);
        voto.setOpcao(opcao);
        votoRepository.save(voto);
    }
}
