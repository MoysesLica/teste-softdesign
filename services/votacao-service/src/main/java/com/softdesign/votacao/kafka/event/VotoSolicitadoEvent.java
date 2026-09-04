package com.softdesign.votacao.kafka.event;

import java.util.UUID;

public record VotoSolicitadoEvent(
    UUID votoId
) {
}
