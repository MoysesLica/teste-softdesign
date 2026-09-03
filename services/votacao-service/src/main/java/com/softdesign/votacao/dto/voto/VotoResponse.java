package com.softdesign.votacao.dto.voto;

import com.softdesign.votacao.model.enums.OpcaoVoto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record VotoResponse (
    UUID id,
    OpcaoVoto opcao,
    String cpf,
    ZonedDateTime dataCriacao
) {
}
