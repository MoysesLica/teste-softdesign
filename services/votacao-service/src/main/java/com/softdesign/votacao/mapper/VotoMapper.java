package com.softdesign.votacao.mapper;

import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.dto.voto.VotoResponse;
import com.softdesign.votacao.model.Voto;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface VotoMapper {
    Voto toEntity(VotoCreateRequest request);
    VotoResponse toResponse(Voto voto);
}