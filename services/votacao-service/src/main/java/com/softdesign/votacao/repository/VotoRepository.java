package com.softdesign.votacao.repository;

import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VotoRepository extends JpaRepository<Voto, UUID> {
    Page<Voto> findByPauta(Pauta pauta, Pageable pageable);
    Page<Voto> findByPautaAndStatusProcessamento(Pauta pauta, StatusProcessamentoVoto statusProcessamento, Pageable pageable);
    Page<Voto> findByPautaAndOpcao(Pauta pauta, OpcaoVoto opcao, Pageable pageable);
    Page<Voto> findByPautaAndStatusProcessamentoAndOpcao(Pauta pauta, StatusProcessamentoVoto statusProcessamento, OpcaoVoto opcao, Pageable pageable);
    boolean existsByPautaAndCpf(Pauta pauta, String cpf);
    boolean existsByPautaAndStatusProcessamento(Pauta pauta, StatusProcessamentoVoto statusProcessamento);
    long countByPautaAndOpcaoAndContabilizadoTrue(Pauta pauta, OpcaoVoto opcao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select voto from Voto voto where voto.id = :id")
    Optional<Voto> findByIdParaProcessamento(@Param("id") UUID id);
}
