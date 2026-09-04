package com.softdesign.votacao.service.v1;

import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.dto.voto.VotoResponse;
import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.exception.RecursoNaoEncontradoException;
import com.softdesign.votacao.mapper.VotoMapper;
import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
public class VotoService {

    private final PautaRepository pautaRepository;
    private final VotoRepository votoRepository;
    private final VotoMapper votoMapper;

    public Page<VotoResponse> listarPorPauta(UUID pautaId, Pageable pageable) {
        Pauta pauta = pautaRepository.findById(pautaId).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        return votoRepository.findByPauta(pauta, pageable).map(votoMapper::toResponse);
    }

    public VotoResponse criar(@Valid VotoCreateRequest votoRequest) {
        Pauta pauta = pautaRepository.findById(votoRequest.pautaId()).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        if(votoRepository.existsByPautaAndCpf(pauta, votoRequest.cpf()))
            throw new PreconditionFailedException("Você já realizou um voto para esta pauta");
        validarPeriodoVotacao(pauta);
        Voto voto = votoMapper.toEntity(votoRequest);
        voto.setPauta(pauta);
        return votoMapper.toResponse(votoRepository.save(voto));
    }

    private void validarPeriodoVotacao(Pauta pauta) {
        ZonedDateTime agora = ZonedDateTime.now();
        if (!pauta.isAbertoParaVotacao())
            throw new PreconditionFailedException("A votação ainda não foi aberta");
        if (!agora.isBefore(pauta.getDataEncerramento()))
            throw new PreconditionFailedException("O prazo para realizar o voto foi encerrado");
        if (!pauta.isAtivo())
            throw new PreconditionFailedException("A pauta está inativa");
    }

}
