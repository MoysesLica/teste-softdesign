package com.softdesign.votacao.model;

import com.softdesign.votacao.model.enums.OpcaoVoto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "voto",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_voto_pauta_cpf",
            columnNames = {"pauta_id", "cpf"}
        )
    }
)
public class Voto extends EntidadeBase{

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "pauta_id",
        nullable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_voto_pauta")
    )
    @Getter @Setter
    private Pauta pauta;

    @Column(nullable = false)
    @Getter @Setter
    private String cpf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter @Setter
    private OpcaoVoto opcao;

}
