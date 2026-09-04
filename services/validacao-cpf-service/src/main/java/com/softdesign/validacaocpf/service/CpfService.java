package com.softdesign.validacaocpf.service;

import com.softdesign.contracts.cpf.CpfValidationResponse;
import com.softdesign.contracts.cpf.CpfValidationStatus;
import com.softdesign.validacaocpf.exception.BadRequestException;
import com.softdesign.validacaocpf.exception.RecursoNaoEncontradoException;
import com.softdesign.validacaocpf.repository.CpfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Validated
public class CpfService {

    private final CpfRepository cpfRepository;

    public CpfValidationResponse validar(String cpfRequest) {
        if(!isCpfValido(cpfRequest))
            throw new BadRequestException("CPF inválido");
        if(!cpfRepository.existsByCpf(cpfRequest))
            throw new RecursoNaoEncontradoException("CPF não cadastrado");
        return new CpfValidationResponse(ThreadLocalRandom.current().nextBoolean() ? CpfValidationStatus.ABLE_TO_VOTE : CpfValidationStatus.UNABLE_TO_VOTE);
    }

    private boolean isCpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}"))
            return false;
        if (cpf.chars().distinct().count() == 1)
            return false;
        int primeiroDigito = calcularDigito(cpf, 9, 10);
        if (primeiroDigito != Character.getNumericValue(cpf.charAt(9)))
            return false;
        int segundoDigito = calcularDigito(cpf, 10, 11);
        return segundoDigito == Character.getNumericValue(cpf.charAt(10));
    }

    private int calcularDigito(String cpf, int quantidadeDigitos, int pesoInicial) {
        int soma = 0;
        for (int indice = 0; indice < quantidadeDigitos; indice++) {
            int numero = Character.getNumericValue(cpf.charAt(indice));
            int peso = pesoInicial - indice;
            soma += numero * peso;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

}
