package com.softdesign.votacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "voto_publicacao",
    indexes = {
        @Index(
            name = "idx_voto_publicacao_data_criacao",
            columnList = "data_criacao"
        )
    }
)
public class VotoPublicacao extends EntidadeBase {

    @Column(nullable = false, unique = true, updatable = false)
    @Getter @Setter
    private UUID votoId;

}
