package com.softdesign.votacao.repository;

import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VotoRepository extends JpaRepository<Voto, UUID> {
    Page<Voto> findByPauta(Pauta pauta, Pageable pageable);
    List<Voto> findByPauta(Pauta pauta);
}
