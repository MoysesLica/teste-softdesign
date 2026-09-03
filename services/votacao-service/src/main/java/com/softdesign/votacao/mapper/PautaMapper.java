package com.softdesign.votacao.mapper;

import com.softdesign.votacao.dto.pauta.PautaCreateRequest;
import com.softdesign.votacao.dto.pauta.PautaResponse;
import com.softdesign.votacao.dto.pauta.PautaPatchRequest;
import com.softdesign.votacao.model.Pauta;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PautaMapper {
    Pauta toEntity(PautaCreateRequest request);
    PautaResponse toResponse(Pauta pauta);
    @BeanMapping(
        ignoreByDefault = true,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "titulo", source = "titulo")
    @Mapping(target = "descricao", source = "descricao")
    void patch(PautaPatchRequest request, @MappingTarget Pauta pauta);

}