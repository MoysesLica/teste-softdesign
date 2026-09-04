package com.softdesign.validacaocpf.repository;

import com.softdesign.validacaocpf.model.Cpf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CpfRepository extends JpaRepository<Cpf, UUID> {

    boolean existsByCpf(String cpfRequest);

    List<Cpf> findAllByCpfIn(Collection<String> cpfs);
}
