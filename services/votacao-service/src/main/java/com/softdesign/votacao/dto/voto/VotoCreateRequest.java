package com.softdesign.votacao.dto.voto;

import com.softdesign.votacao.model.enums.OpcaoVoto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.br.CPF;

import java.util.UUID;

public record VotoCreateRequest(
    @NotBlank(message = "O CPF é obrigatório")
    @Pattern(
        regexp = "\\d{11}",
        message = "O CPF deve conter exatamente 11 números"
    )
    @CPF(message = "O CPF informado é inválido")
    String cpf,
    @NotNull(message = "A opção de voto é obrigatória")
    OpcaoVoto opcao,
    @NotNull(message = "O ID da pauta é obrigatório")
    UUID pautaId
) {
}
