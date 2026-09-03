package com.softdesign.votacao.repository;

import com.softdesign.votacao.model.Pauta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.ZonedDateTime;
import java.util.UUID;

public interface PautaRepository extends JpaRepository<Pauta, UUID>, JpaSpecificationExecutor<Pauta> {
    long countByDataAberturaGreaterThanEqualAndDataAberturaLessThan(ZonedDateTime inicio, ZonedDateTime fim);
}
