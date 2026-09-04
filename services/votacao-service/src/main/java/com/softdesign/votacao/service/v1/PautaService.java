package com.softdesign.votacao.service.v1;

import com.softdesign.votacao.dto.pauta.PautaCreateRequest;
import com.softdesign.votacao.dto.pauta.PautaOpenToVotingRequest;
import com.softdesign.votacao.dto.pauta.PautaResponse;
import com.softdesign.votacao.dto.pauta.PautaPatchRequest;
import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.exception.RecursoNaoEncontradoException;
import com.softdesign.votacao.mapper.PautaMapper;
import com.softdesign.votacao.model.Pauta;
import com.softdesign.votacao.model.enums.OpcaoVoto;
import com.softdesign.votacao.model.enums.StatusPauta;
import com.softdesign.votacao.model.enums.StatusProcessamentoVoto;
import com.softdesign.votacao.repository.PautaRepository;
import com.softdesign.votacao.repository.VotoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static com.softdesign.votacao.repository.specification.PautaSpecification.comAtivo;

@Service
@RequiredArgsConstructor
@Validated
public class PautaService {

    private final PautaRepository pautaRepository;
    private final VotoRepository votoRepository;
    private final PautaMapper pautaMapper;
    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    public Page<PautaResponse> listar(Boolean ativo, Pageable pageable) {
        return pautaRepository.findBy(comAtivo(ativo), query -> query.page(pageable)).map(pautaMapper::toResponse);
    }

    public PautaResponse criar(@Valid PautaCreateRequest pautaRequest) {
        Pauta pauta = pautaMapper.toEntity(pautaRequest);
        pauta.setCodigo(gerarCodigo());
        return pautaMapper.toResponse(pautaRepository.save(pauta));
    }

    public PautaResponse atualizar(UUID id, @Valid PautaPatchRequest pautaRequest) {
        Pauta pauta = pautaRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        pautaMapper.patch(pautaRequest, pauta);
        return pautaMapper.toResponse(pautaRepository.save(pauta));
    }

    private String gerarCodigo() {
        ZonedDateTime aberturaNaZona = ZonedDateTime.now().withZoneSameInstant(ZONA_NEGOCIO);
        YearMonth competencia = YearMonth.from(aberturaNaZona);
        ZonedDateTime inicio = competencia.atDay(1).atStartOfDay(ZONA_NEGOCIO);
        ZonedDateTime fim = competencia.plusMonths(1).atDay(1).atStartOfDay(ZONA_NEGOCIO);
        long quantidade = pautaRepository.countByDataAberturaGreaterThanEqualAndDataAberturaLessThan(inicio, fim);
        return "%04d_%02d_%04d".formatted(competencia.getYear(), competencia.getMonthValue(), quantidade + 1);
    }

    public void inativar(UUID id) {
        Pauta pauta = pautaRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        pauta.setAtivo(false);
        pautaRepository.save(pauta);
    }

    public PautaResponse detalhes(UUID id) {
        Pauta pauta = pautaRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        return pautaMapper.toResponse(pauta);
    }

    public PautaResponse abrirParaVotacao(UUID id, @Valid PautaOpenToVotingRequest pautaRequest) {
        Pauta pauta = pautaRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        if(pauta.isFechada())
            throw new PreconditionFailedException("Pauta já fechada para votação");
        if(pauta.isAbertoParaVotacao())
            throw new PreconditionFailedException("Pauta já aberta para votação");
        pauta.setAbertoParaVotacao(true);
        pauta.setDataAbertura(ZonedDateTime.now());
        pauta.setDataEncerramento(pautaRequest.dataEncerramento() != null ? pautaRequest.dataEncerramento() : ZonedDateTime.now().plusMinutes(1));
        return pautaMapper.toResponse(pautaRepository.save(pauta));
    }

    @Transactional
    public PautaResponse fecharVotacao(UUID id) {
        Pauta pauta = pautaRepository.findByIdParaFechamento(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        if(!pauta.isAbertoParaVotacao())
            throw new PreconditionFailedException("Pauta não aberta para votação");
        if(pauta.isFechada())
            throw new PreconditionFailedException("Pauta já fechada");
        if(votoRepository.existsByPautaAndStatusProcessamento(pauta, StatusProcessamentoVoto.PENDENTE))
            throw new PreconditionFailedException("Existem votos aguardando processamento");
        if(ZonedDateTime.now().isBefore(pauta.getDataEncerramento()))
            pauta.setDataEncerramento(ZonedDateTime.now());
        pauta.setAbertoParaVotacao(false);
        pauta.setFechada(true);
        long sins = votoRepository.countByPautaAndOpcaoAndContabilizadoTrue(pauta, OpcaoVoto.SIM);
        long naos = votoRepository.countByPautaAndOpcaoAndContabilizadoTrue(pauta, OpcaoVoto.NAO);
        pauta.setVotosSim(sins);
        pauta.setVotosNao(naos);
        pauta.setStatus(sins > naos ? StatusPauta.APROVADO : StatusPauta.REPROVADO);
        return pautaMapper.toResponse(pautaRepository.save(pauta));
    }

}
