package com.softdesign.votacao.repository;

import com.softdesign.votacao.model.VotoPublicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VotoPublicacaoRepository extends JpaRepository<VotoPublicacao, UUID> {

    @Query(
        value = "SELECT * FROM voto_publicacao ORDER BY data_criacao LIMIT :limite FOR UPDATE SKIP locked",
        nativeQuery = true
    )
    List<VotoPublicacao> buscarParaPublicacao(@Param("limite") int limite);

}
