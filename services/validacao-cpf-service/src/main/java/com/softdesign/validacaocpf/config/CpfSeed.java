package com.softdesign.validacaocpf.config;

import com.softdesign.validacaocpf.model.Cpf;
import com.softdesign.validacaocpf.repository.CpfRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CpfSeed implements ApplicationRunner {

    private static final String CPF_REFERENCIA = "03425110250";
    private static final int TAMANHO_LOTE_CONSULTA = 1000;

    private final CpfRepository cpfRepository;

    @Value("${seed.quantidade-cpfs:500}")
    private int quantidadeCpfs;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        List<String> cpfs = gerarCpfs();
        Set<String> cpfsCadastrados = buscarCpfsCadastrados(cpfs);

        List<Cpf> novosCpfs = cpfs.stream()
            .filter(cpf -> !cpfsCadastrados.contains(cpf))
            .map(this::criarCpf)
            .toList();

        if (!novosCpfs.isEmpty()) {
            cpfRepository.saveAll(novosCpfs);
        }
    }

    private Set<String> buscarCpfsCadastrados(List<String> cpfs) {
        Set<String> cadastrados = new LinkedHashSet<>();
        for (int inicio = 0; inicio < cpfs.size(); inicio += TAMANHO_LOTE_CONSULTA) {
            int fim = Math.min(inicio + TAMANHO_LOTE_CONSULTA, cpfs.size());
            cadastrados.addAll(cpfRepository.findAllByCpfIn(cpfs.subList(inicio, fim)).stream()
                .map(Cpf::getCpf)
                .collect(Collectors.toSet()));
        }
        return cadastrados;
    }

    private List<String> gerarCpfs() {
        Set<String> cpfs = new LinkedHashSet<>(quantidadeCpfs);
        cpfs.add(CPF_REFERENCIA);

        for (int sequencial = 1; cpfs.size() < quantidadeCpfs; sequencial++) {
            cpfs.add(gerarCpf(sequencial));
        }

        return List.copyOf(cpfs);
    }

    private String gerarCpf(int sequencial) {
        String base = "%09d".formatted(sequencial);
        String primeiroDigito = String.valueOf(calcularDigito(base));
        String segundoDigito = String.valueOf(calcularDigito(base + primeiroDigito));
        return base + primeiroDigito + segundoDigito;
    }

    private int calcularDigito(String cpf) {
        int soma = 0;
        int peso = cpf.length() + 1;

        for (int indice = 0; indice < cpf.length(); indice++) {
            int numero = Character.getNumericValue(cpf.charAt(indice));
            soma += numero * peso--;
        }

        int digito = 11 - (soma % 11);
        return digito >= 10 ? 0 : digito;
    }

    private Cpf criarCpf(String numero) {
        Cpf cpf = new Cpf();
        cpf.setCpf(numero);
        return cpf;
    }
}
