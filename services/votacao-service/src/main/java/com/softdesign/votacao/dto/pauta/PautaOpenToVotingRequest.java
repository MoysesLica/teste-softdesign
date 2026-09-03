package com.softdesign.votacao.dto.pauta;

import jakarta.validation.constraints.Future;

import java.time.ZonedDateTime;

public record PautaOpenToVotingRequest (
    @Future(message = "Data de encerramento deve ser maior que o momento atual")
    ZonedDateTime dataEncerramento
) {
}
