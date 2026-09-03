package com.softdesign.votacao.controller.v1;

import com.softdesign.votacao.dto.pauta.PautaCreateRequest;
import com.softdesign.votacao.dto.pauta.PautaOpenToVotingRequest;
import com.softdesign.votacao.dto.pauta.PautaResponse;
import com.softdesign.votacao.dto.pauta.PautaPatchRequest;
import com.softdesign.votacao.service.PautaService;
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
    name = "Pautas"
)
@RestController
@RequestMapping("/api/v1/pautas")
@RequiredArgsConstructor
public class PautaController {

    private final PautaService pautaService;

    @Operation(summary = "Listar pautas", description = "Lista de pautas paginada, com filtro ativo opcional")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de pautas retornada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<PautaResponse>> listar(
        @RequestParam(required = false) Boolean ativo,
        @ParameterObject @PageableDefault(size = 20, sort = "codigo", direction = Sort.Direction.ASC) Pageable pageable
    ){
        return ResponseEntity.ok(new PagedModel<>(pautaService.listar(ativo, pageable)));
    }

    @Operation(summary = "Buscar uma pauta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pauta retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<PautaResponse> detalhes(@PathVariable UUID id){
        return ResponseEntity.ok(pautaService.detalhes(id));
    }

    @Operation(summary = "Criação de pauta")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pauta criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<PautaResponse> criar(@RequestBody @Valid PautaCreateRequest pauta){
        PautaResponse response = pautaService.criar(pauta);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Atualização de pauta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pauta atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<PautaResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid PautaPatchRequest pauta){
        return ResponseEntity.ok(pautaService.atualizar(id, pauta));
    }

    @Operation(summary = "Inativação de pauta")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Pauta inativada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id){
        pautaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Abrir para votação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pauta aberta para votação"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "412", description = "Pauta já aberta para votação", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/open")
    public ResponseEntity<PautaResponse> abrirParaVotacao(@PathVariable UUID id, @RequestBody @Valid PautaOpenToVotingRequest pauta){
        return ResponseEntity.ok(pautaService.abrirParaVotacao(id, pauta));
    }

    @Operation(summary = "Fechar votação e contabilizar votos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pauta fechada e contabilizado votos"),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "412", description = "Pauta não aberta para votação", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/close")
    public ResponseEntity<PautaResponse> fecharVotacao(@PathVariable UUID id){
        return ResponseEntity.ok(pautaService.fecharVotacao(id));
    }

}
