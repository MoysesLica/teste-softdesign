package com.softdesign.votacao.dto.pauta;

import jakarta.validation.constraints.NotBlank;

public record PautaCreateRequest(
        @NotBlank(message = "O título é obrigatório")
        String titulo,
        @NotBlank(message = "A descrição é obrigatória")
        String descricao
) {

}
