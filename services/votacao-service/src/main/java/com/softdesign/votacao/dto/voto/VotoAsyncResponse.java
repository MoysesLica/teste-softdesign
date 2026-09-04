package com.softdesign.votacao.dto.voto;

import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record VotoAsyncResponse(
    UUID id,
    OpcaoVoto opcao,
    String cpf,
    boolean cpfValidado,
    boolean aptoParaVotar,
    boolean contabilizado,
    StatusProcessamentoVoto statusProcessamento,
    ZonedDateTime dataCriacao,
    ZonedDateTime dataAtualizacao
) {
}
