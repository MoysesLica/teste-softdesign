package com.softdesign.validacaocpf.controller.v1;

import com.softdesign.contracts.cpf.CpfValidationResponse;
import com.softdesign.validacaocpf.service.CpfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "Pautas"
)
@RestController
@RequestMapping("/api/v1/cpf")
@RequiredArgsConstructor
public class CpfController {

    private final CpfService cpfService;

    @Operation(summary = "Validar se CPF está apto para votar")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultado da validação"),
        @ApiResponse(responseCode = "400", description = "CPF inválido", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "CPF não encontrado", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{cpf}")
    public ResponseEntity<CpfValidationResponse> validar(@PathVariable String cpf){
        return ResponseEntity.ok(cpfService.validar(cpf));
    }

}
