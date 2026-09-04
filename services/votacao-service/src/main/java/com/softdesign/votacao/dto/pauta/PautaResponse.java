package com.softdesign.votacao.dto.pauta;

import com.softdesign.votacao.model.enums.StatusPauta;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PautaResponse(
        UUID id,
        String titulo,
        String descricao,
        boolean abertoParaVotacao,
        boolean fechada,
        ZonedDateTime dataAbertura,
        ZonedDateTime dataEncerramento,
        String codigo,
        StatusPauta status,
        long votosSim,
        long votosNao,
        boolean ativo,
        ZonedDateTime dataCriacao,
        ZonedDateTime dataAtualizacao
) {
}
