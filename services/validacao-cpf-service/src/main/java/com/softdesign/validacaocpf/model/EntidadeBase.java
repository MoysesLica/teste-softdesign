package com.softdesign.validacaocpf.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.ZonedDateTime;
import java.util.UUID;

@MappedSuperclass
public abstract class EntidadeBase {
    @Id
    @UuidGenerator
    @Column(nullable = false)
    @Getter @Setter
    private UUID id;

    @Column(nullable = false)
    @CreationTimestamp
    @Getter
    private ZonedDateTime dataCriacao;

    @Column(nullable = false)
    @UpdateTimestamp
    @Getter
    private ZonedDateTime dataAtualizacao;

    @Column(nullable = false)
    @Getter @Setter
    private boolean ativo = true;
}
