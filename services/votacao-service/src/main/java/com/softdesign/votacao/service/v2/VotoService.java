package com.softdesign.votacao.service.v2;

import com.softdesign.contracts.cpf.CpfValidationResponse;
import com.softdesign.contracts.cpf.CpfValidationStatus;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;

@Service("VotoServiceV2")
@RequiredArgsConstructor
@Validated
public class VotoService {

    private final PautaRepository pautaRepository;
    private final VotoRepository votoRepository;
    private final VotoMapper votoMapper;
    private final RestClient cpfValidationRestClient;

    public VotoResponse criar(@Valid VotoCreateRequest votoRequest) {
        Pauta pauta = pautaRepository.findById(votoRequest.pautaId()).orElseThrow(() -> new RecursoNaoEncontradoException("Pauta não encontrada"));
        if(votoRepository.existsByPautaAndCpf(pauta, votoRequest.cpf()))
            throw new PreconditionFailedException("Você já realizou um voto para esta pauta");
        validarPeriodoVotacao(pauta);
        validarCpfAptoParaVotar(votoRequest.cpf());
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

    private void validarCpfAptoParaVotar(String cpf) {
        CpfValidationResponse response = cpfValidationRestClient.get().uri("/api/v1/cpf/{cpf}", cpf).retrieve()
                .onStatus(status -> status.value() == 400,
                    (_, _) -> {throw new PreconditionFailedException("CPF inválido");}
                )
                .onStatus(status -> status.value() == 404,
                    (_, _) -> {throw new PreconditionFailedException("CPF não encontrado");}
                )
                .onStatus(
                    status -> status.value() == 503,
                    (_, _) -> {throw new PreconditionFailedException("O serviço de validação de CPF está indisponível");}
                )
                .onStatus(
                    HttpStatusCode::isError,
                    (_, _) -> {throw new PreconditionFailedException("Erro inesperado");}
                )
                .body(CpfValidationResponse.class);

        if (response == null || response.status() != CpfValidationStatus.ABLE_TO_VOTE)
            throw new PreconditionFailedException("CPF não está apto para votar");

    }

}
