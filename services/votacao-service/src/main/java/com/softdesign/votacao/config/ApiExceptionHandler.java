package com.softdesign.votacao.config;

import java.util.List;

import com.softdesign.votacao.exception.PreconditionFailedException;
import com.softdesign.votacao.exception.RecursoNaoEncontradoException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getAllErrors().stream().map(this::toValidationError).toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Um ou mais campos estão inválidos.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleRecursoNaoEncontrado(RecursoNaoEncontradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public ProblemDetail handlePreconditionFailed(PreconditionFailedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, exception.getMessage());
    }

    private ValidationError toValidationError(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : null;
        return new ValidationError(field, error.getCode(), error.getDefaultMessage());
    }

    private record ValidationError(String field, String code, String message) {
    }
}