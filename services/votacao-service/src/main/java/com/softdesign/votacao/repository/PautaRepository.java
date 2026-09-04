package com.softdesign.votacao.repository;

import com.softdesign.votacao.model.Pauta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PautaRepository extends JpaRepository<Pauta, UUID>, JpaSpecificationExecutor<Pauta> {
    long countByDataAberturaGreaterThanEqualAndDataAberturaLessThan(ZonedDateTime inicio, ZonedDateTime fim);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select pauta from Pauta pauta where pauta.id = :id")
    Optional<Pauta> findByIdParaVoto(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pauta from Pauta pauta where pauta.id = :id")
    Optional<Pauta> findByIdParaFechamento(@Param("id") UUID id);
}
