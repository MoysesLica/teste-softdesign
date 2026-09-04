package com.softdesign.votacao.controller.v2;

import com.softdesign.votacao.dto.voto.VotoCreateRequest;
import com.softdesign.votacao.dto.voto.VotoResponse;
import com.softdesign.votacao.service.v2.VotoService;
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

@Tag(
    name = "Votos"
)
@RestController("VotoControllerV2")
@RequestMapping("/api/v2/votos")
@RequiredArgsConstructor
public class VotoController {

    private final VotoService votoService;

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
