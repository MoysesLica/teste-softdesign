package com.softdesign.validacaocpf;

import com.softdesign.validacaocpf.config.CpfSeed;
import com.softdesign.validacaocpf.repository.CpfRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:validacao-cpf;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "seed.quantidade-cpfs=500"
})
@AutoConfigureMockMvc
class ValidacaoCpfServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CpfRepository cpfRepository;
    @Autowired
    private CpfSeed cpfSeed;

    // Teste rápido para garantir que o serviço inicia com banco, controller e seed configurados.
    @Test
    void contextLoads() {
    }

    // Um CPF cadastrado deve retornar uma das duas situações previstas pelo contrato externo.
    @Test
    void deveRetornarSituacaoParaCpfValidoECadastrado() throws Exception {
        mockMvc.perform(get("/api/v1/cpf/{cpf}", "03425110250"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.status", anyOf(is("ABLE_TO_VOTE"), is("UNABLE_TO_VOTE"))));
    }

    // CPF bem formado, mas ausente no cadastro, precisa ser tratado como recurso não encontrado.
    @Test
    void deveRetornarNotFoundParaCpfValidoNaoCadastrado() throws Exception {
        mockMvc.perform(get("/api/v1/cpf/{cpf}", "52998224725"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.detail").value("CPF não cadastrado"));
    }

    // Formato incorreto, dígitos errados e números repetidos são rejeitados como CPF inválido.
    @Test
    void deveRejeitarCpfComFormatoOuDigitosInvalidos() throws Exception {
        mockMvc.perform(get("/api/v1/cpf/{cpf}", "123"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("CPF inválido"));

        mockMvc.perform(get("/api/v1/cpf/{cpf}", "03425110251"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/cpf/{cpf}", "11111111111"))
            .andExpect(status().isBadRequest());
    }

    // Rodar o seed novamente deve completar dados faltantes sem criar registros duplicados.
    @Test
    void deveExecutarSeedMaisDeUmaVezSemDuplicarCpfs() throws Exception {
        long quantidadeInicial = cpfRepository.count();

        cpfSeed.run(new DefaultApplicationArguments(new String[0]));
        cpfSeed.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(quantidadeInicial, cpfRepository.count());
    }

}
