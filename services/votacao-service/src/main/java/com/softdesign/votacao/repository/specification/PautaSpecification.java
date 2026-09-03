package com.softdesign.votacao.repository.specification;

import com.softdesign.votacao.model.Pauta;
import org.springframework.data.jpa.domain.PredicateSpecification;

public class PautaSpecification {

    private PautaSpecification() {
    }

    public static PredicateSpecification<Pauta> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return PredicateSpecification.unrestricted();
        }

        return (from, criteriaBuilder) ->
                criteriaBuilder.equal(from.get("ativo"), ativo);
    }

}
