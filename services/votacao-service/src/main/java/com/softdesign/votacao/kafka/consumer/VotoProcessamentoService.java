package com.softdesign.votacao.kafka.consumer;

import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.VotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VotoProcessamentoService {

    private final VotoRepository votoRepository;

    public Voto buscarPendente(UUID votoId) {
        Voto voto = votoRepository.findById(votoId).orElse(null);
        if (voto == null || voto.getStatusProcessamento() != StatusProcessamentoVoto.PENDENTE)
            return null;
        return voto;
    }

    @Transactional
    public void finalizar(UUID votoId, StatusProcessamentoVoto status) {
        Voto voto = votoRepository.findByIdParaProcessamento(votoId).orElse(null);
        if (voto == null || voto.getStatusProcessamento() != StatusProcessamentoVoto.PENDENTE)
            return;

        boolean contabilizado = status == StatusProcessamentoVoto.CONTABILIZADO;
        boolean cpfValidado = contabilizado || status == StatusProcessamentoVoto.NAO_APTO;
        voto.setCpfValidado(cpfValidado);
        voto.setAptoParaVotar(contabilizado);
        voto.setContabilizado(contabilizado);
        voto.setStatusProcessamento(status);
        votoRepository.save(voto);
    }

}
