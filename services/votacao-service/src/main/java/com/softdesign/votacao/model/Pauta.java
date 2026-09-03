package com.softdesign.votacao.model;

import com.softdesign.votacao.model.enums.StatusPauta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pauta")
public class Pauta extends EntidadeBase{

    @Column(nullable = false)
    @Getter @Setter
    private String titulo;

    @Column(nullable = false)
    @Getter @Setter
    private String descricao;

    @Column(nullable = false)
    @Getter @Setter
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter @Setter
    private StatusPauta status = StatusPauta.PENDENTE;

    @Column(nullable = false)
    @Getter @Setter
    private boolean abertoParaVotacao = false;

    @Column(nullable = false)
    @Getter @Setter
    private boolean fechada = false;

    @Column
    @Getter @Setter
    private ZonedDateTime dataAbertura;

    @Column
    @Getter @Setter
    private ZonedDateTime dataEncerramento;

}
