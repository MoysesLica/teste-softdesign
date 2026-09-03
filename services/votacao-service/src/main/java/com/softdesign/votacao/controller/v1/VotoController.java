package com.softdesign.votacao.controller.v1;

import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.dto.voto.VotoResponse;
import com.softdesign.votacao.service.VotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(
    name = "Votos"
)
@RestController
@RequestMapping("/api/v1/votos")
@RequiredArgsConstructor
public class VotoController {

    private final VotoService votoService;

    @Operation(summary = "Listar votos", description = "Lista de votos por pauta, com paginação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de votos retornada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{pautaId}")
    public ResponseEntity<PagedModel<VotoResponse>> listarPorPauta(
        @PathVariable UUID pautaId,
        @ParameterObject @PageableDefault(size = 20, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable
    ){
        return ResponseEntity.ok(new PagedModel<>(votoService.listarPorPauta(pautaId, pageable)));
    }

    @Operation(summary = "Realizar voto")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Voto realizado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<VotoResponse> criar(@RequestBody @Valid VotoCreateRequest voto){
        VotoResponse response = votoService.criar(voto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

}
