package com.softdesign.votacao.model;

import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "voto",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_voto_pauta_cpf",
            columnNames = {"pauta_id", "cpf"}
        )
    },
    indexes = {
        @Index(
            name = "idx_voto_pauta_opcao_contabilizado",
            columnList = "pauta_id, opcao, contabilizado"
        ),
        @Index(
            name = "idx_voto_pauta_status_processamento",
            columnList = "pauta_id, status_processamento"
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

    @Column(nullable = false)
    @ColumnDefault("false")
    @Getter @Setter
    private boolean cpfValidado = false;

    @Column(nullable = false)
    @ColumnDefault("false")
    @Getter @Setter
    private boolean aptoParaVotar = false;

    @Column(nullable = false)
    @ColumnDefault("true")
    @Getter @Setter
    private boolean contabilizado = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'CONTABILIZADO'")
    @Getter @Setter
    private StatusProcessamentoVoto statusProcessamento = StatusProcessamentoVoto.CONTABILIZADO;

}
