package com.softdesign.votacao.controller.v3;

import com.softdesign.votacao.dto.voto.VotoAsyncResponse;
import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.service.v3.VotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(
    name = "Votos"
)
@RestController("VotoControllerV3")
@RequestMapping("/api/v3/votos")
@RequiredArgsConstructor
public class VotoController {

    private final VotoService votoService;

    @Operation(summary = "Realizar voto de forma assíncrona")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Voto recebido para processamento"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Pauta não encontrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "412", description = "Voto ou pauta não atendem às condições necessárias", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<VotoAsyncResponse> criar(@RequestBody @Valid VotoCreateRequest voto){
        VotoAsyncResponse response = votoService.criar(voto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Consultar processamento do voto")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Voto retornado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Voto não encontrado", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<VotoAsyncResponse> detalhes(@PathVariable UUID id){
        return ResponseEntity.ok(votoService.detalhes(id));
    }

}
