package com.softdesign.validacaocpf.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "cpf",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_cpf",
            columnNames = "cpf"
        )
    }
)
public class Cpf extends EntidadeBase{

    @Column(nullable = false, unique = true, length = 11)
    @Getter @Setter
    private String cpf;

}
