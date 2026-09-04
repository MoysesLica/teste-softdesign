package com.softdesign.votacao.service.v3;

import com.softdesign.votacao.dto.voto.VotoAsyncResponse;
import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.exception.RecursoNaoEncontradoException;
import com.softdesign.votacao.mapper.VotoMapper;
import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.Voto;
import com.softdesign.votacao.model.VotoPublicacao;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoPublicacaoRepository;
import com.softdesign.votacao.repository.VotoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service("VotoServiceV3")
@RequiredArgsConstructor
@Validated
public class VotoService {

    private final PautaRepository pautaRepository;
    private final VotoRepository votoRepository;
    private final VotoPublicacaoRepository votoPublicacaoRepository;
    private final VotoMapper votoMapper;

    @Transactional
    public VotoAsyncResponse criar(@Valid VotoCreateRequest votoRequest) {
        Pauta pauta = pautaRepository.findByIdParaVoto(votoRequest.pautaId()).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        if(votoRepository.existsByPautaAndCpf(pauta, votoRequest.cpf()))
            throw new PreconditionFailedException("Você já realizou um voto para esta pauta");
        validarPeriodoVotacao(pauta);
        Voto voto = votoMapper.toEntity(votoRequest);
        voto.setPauta(pauta);
        voto.setCpfValidado(false);
        voto.setAptoParaVotar(false);
        voto.setContabilizado(false);
        voto.setStatusProcessamento(StatusProcessamentoVoto.PENDENTE);
        Voto votoSalvo;
        try {
            votoSalvo = votoRepository.saveAndFlush(voto);
        } catch (DataIntegrityViolationException exception) {
            throw new PreconditionFailedException("Você já realizou um voto para esta pauta");
        }
        VotoPublicacao publicacao = new VotoPublicacao();
        publicacao.setVotoId(votoSalvo.getId());
        votoPublicacaoRepository.save(publicacao);
        return votoMapper.toAsyncResponse(votoSalvo);
    }

    public VotoAsyncResponse detalhes(UUID id) {
        Voto voto = votoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Voto não encontrado"));
        return votoMapper.toAsyncResponse(voto);
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
